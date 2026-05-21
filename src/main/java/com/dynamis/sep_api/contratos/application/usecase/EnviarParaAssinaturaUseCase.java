package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.application.port.out.dto.RequisicaoEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.RespostaEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.service.HashContratoService;
import com.dynamis.sep_api.contratos.application.service.ccb.CcbGenerator;
import com.dynamis.sep_api.contratos.application.service.ccb.CcbTemplate;
import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.EnvelopeAssinatura;
import com.dynamis.sep_api.contratos.domain.model.VersaoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EnvelopeAssinaturaRepository;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: envia a CCB do contrato aceito ao provider de assinatura digital (Sprint 11 Task
 * 11.5).
 *
 * <p>Fluxo:
 * <ol>
 *   <li>Carrega o contrato com lock pessimista; exige status {@code ACEITO} ou {@code
 *       EM_ASSINATURA} (idempotencia).
 *   <li>Se ja existe envelope para a versao vigente, devolve sem chamar o provider novamente
 *       (idempotencia por {@code idempotencyKey = contratoId:vN}).
 *   <li>Gera o PDF da CCB via {@link CcbGenerator}; calcula SHA-256 do PDF.
 *   <li>Chama {@link AssinaturaDigitalProvider#enviarParaAssinatura} com PDF + signatario.
 *   <li>Persiste {@link EnvelopeAssinatura} (nasce ENVIADO) e transiciona contrato para {@code
 *       EM_ASSINATURA}.
 * </ol>
 *
 * <p>Eventos de auditoria (CCB_GERADA / ASSINATURA_ENVIADA) ficam pra Task 11.8 — esta task expoe
 * apenas a integracao com Provider Pattern. Webhook/callback fica em Task 11.6/11.5
 * (ProcessarCallbackAssinaturaUseCase).
 */
@Service
public class EnviarParaAssinaturaUseCase {

    private static final Logger log = LoggerFactory.getLogger(EnviarParaAssinaturaUseCase.class);
    private static final String PROVIDER_NAME = "clicksign";

    private final ContratoLoaderService contratoLoader;
    private final EnvelopeAssinaturaRepository envelopeRepository;
    private final PropostaCreditoRepository propostaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CcbGenerator ccbGenerator;
    private final AssinaturaDigitalProvider provider;
    private final HashContratoService hashService;

    public EnviarParaAssinaturaUseCase(
            ContratoLoaderService contratoLoader,
            EnvelopeAssinaturaRepository envelopeRepository,
            PropostaCreditoRepository propostaRepository,
            UsuarioRepository usuarioRepository,
            CcbGenerator ccbGenerator,
            AssinaturaDigitalProvider provider,
            HashContratoService hashService) {
        this.contratoLoader = contratoLoader;
        this.envelopeRepository = envelopeRepository;
        this.propostaRepository = propostaRepository;
        this.usuarioRepository = usuarioRepository;
        this.ccbGenerator = ccbGenerator;
        this.provider = provider;
        this.hashService = hashService;
    }

    @Transactional
    public EnvelopeAssinatura executar(UUID contratoId, String correlationId) {
        Objects.requireNonNull(contratoId, "contratoId obrigatorio");

        Contrato contrato = contratoLoader.carregarComLock(contratoId);
        VersaoContrato versao = contrato.versaoVigente()
                .orElseThrow(() -> new ContratoEstadoInvalidoException("envioAssinatura", contrato.getStatus()));

        String idempotencyKey = contrato.getId() + ":v" + versao.getNumero();

        // Idempotencia: envelope da versao ja existe → devolve sem chamar provider
        var existente = envelopeRepository.findByIdempotencyKey(idempotencyKey);
        if (existente.isPresent()) {
            log.info(
                    "EnviarParaAssinatura idempotente — envelope ja existe contratoId={} envelopeId={}",
                    contratoId,
                    existente.get().getId());
            return existente.get();
        }

        if (!contrato.getStatus().permiteEnvioAssinatura()) {
            throw new ContratoEstadoInvalidoException("enviarParaAssinatura", contrato.getStatus());
        }

        PropostaCredito proposta = propostaRepository
                .findById(contrato.getPropostaId())
                .orElseThrow(() -> new IllegalStateException("Proposta nao encontrada para contrato " + contratoId));
        Usuario tomador = usuarioRepository
                .findById(contrato.getTomadorId())
                .orElseThrow(() -> new IllegalStateException("Tomador nao encontrado para contrato " + contratoId));

        byte[] pdf = ccbGenerator.gerar(CcbTemplate.de(contrato, versao, proposta, OffsetDateTime.now()));
        String hashPdf = hashService.calcular(pdf);

        RequisicaoEnvioAssinatura req = new RequisicaoEnvioAssinatura(
                contrato.getId(), versao.getId(), tomador.getUsername(), tomador.getUsername(), idempotencyKey);
        RespostaEnvioAssinatura resp = provider.enviarParaAssinatura(pdf, req, correlationId);

        EnvelopeAssinatura envelope = EnvelopeAssinatura.criar(
                contrato.getId(),
                versao.getId(),
                PROVIDER_NAME,
                resp.idEnvelopeExterno(),
                idempotencyKey,
                hashPdf,
                resp.dataEnvio());
        envelope = envelopeRepository.save(envelope);
        contrato.marcarEmAssinatura();

        log.info(
                "Contrato enviado para assinatura contratoId={} envelopeId={} idEnvelopeExterno={}",
                contratoId,
                envelope.getId(),
                envelope.getIdEnvelopeExterno());
        return envelope;
    }
}
