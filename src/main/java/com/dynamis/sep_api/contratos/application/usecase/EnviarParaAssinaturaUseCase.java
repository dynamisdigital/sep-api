package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.application.port.out.dto.RequisicaoEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.RespostaEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.service.HashContratoService;
import com.dynamis.sep_api.contratos.application.service.ccb.CcbGenerator;
import com.dynamis.sep_api.contratos.application.service.ccb.CcbTemplate;
import com.dynamis.sep_api.contratos.domain.event.AssinaturaEnviadaEvent;
import com.dynamis.sep_api.contratos.domain.event.CcbGeradaEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public EnviarParaAssinaturaUseCase(
            ContratoLoaderService contratoLoader,
            EnvelopeAssinaturaRepository envelopeRepository,
            PropostaCreditoRepository propostaRepository,
            UsuarioRepository usuarioRepository,
            CcbGenerator ccbGenerator,
            AssinaturaDigitalProvider provider,
            HashContratoService hashService,
            ApplicationEventPublisher eventPublisher) {
        this.contratoLoader = contratoLoader;
        this.envelopeRepository = envelopeRepository;
        this.propostaRepository = propostaRepository;
        this.usuarioRepository = usuarioRepository;
        this.ccbGenerator = ccbGenerator;
        this.provider = provider;
        this.hashService = hashService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public EnvelopeAssinatura executar(UUID contratoId, String correlationId) {
        Objects.requireNonNull(contratoId, "contratoId obrigatorio");

        Contrato contrato = contratoLoader.carregarComLock(contratoId);
        VersaoContrato versao = contrato.versaoVigente()
                .orElseThrow(() -> new ContratoEstadoInvalidoException("envioAssinatura", contrato.getStatus()));

        String idempotencyKey = contrato.getId() + ":v" + versao.getNumero();

        // Ordem (fix C1 review Task 11.5): valida estado ANTES de consultar envelope existente.
        // Idempotencia em EM_ASSINATURA: se envelope ja existe pra essa versao, devolve sem chamar
        // o provider. Estados final/cancelado rejeitados explicitamente.
        if (!contrato.getStatus().permiteEnvioAssinatura()
                && contrato.getStatus() != com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao.EM_ASSINATURA) {
            throw new ContratoEstadoInvalidoException("enviarParaAssinatura", contrato.getStatus());
        }

        var existente = envelopeRepository.findByIdempotencyKey(idempotencyKey);
        if (existente.isPresent()) {
            log.info(
                    "EnviarParaAssinatura idempotente — envelope ja existe contratoId={} envelopeId={}",
                    contratoId,
                    existente.get().getId());
            return existente.get();
        }

        // Sanity: estado ACEITO obrigatorio pra criar envelope novo (EM_ASSINATURA so com
        // envelope existente — pego no bloco anterior).
        if (!contrato.getStatus().permiteEnvioAssinatura()) {
            throw new ContratoEstadoInvalidoException("enviarParaAssinatura", contrato.getStatus());
        }

        PropostaCredito proposta = propostaRepository
                .findById(contrato.getPropostaId())
                .orElseThrow(() -> new ContratoEstadoInvalidoException(
                        "enviarParaAssinatura.propostaAusente", contrato.getStatus()));
        Usuario tomador = usuarioRepository
                .findById(contrato.getTomadorId())
                .orElseThrow(() -> new ContratoEstadoInvalidoException(
                        "enviarParaAssinatura.tomadorAusente", contrato.getStatus()));

        byte[] pdf = ccbGenerator.gerar(CcbTemplate.de(contrato, versao, proposta, OffsetDateTime.now()));
        String hashPdf = hashService.calcular(pdf);
        eventPublisher.publishEvent(new CcbGeradaEvent(
                contrato.getId(),
                contrato.getPropostaId(),
                contrato.getTomadorId(),
                versao.getId(),
                versao.getNumero(),
                hashPdf));

        // Fix C5 review Task 11.5: signatario email != nome. Usuario.username eh email (validado
        // em Sprint 2); nome deriva do prefixo do email + sufixo UUID — placeholder seguro ate
        // onboarding (Epic 5) expor nome real. Pendencia documentada em CCB.md/CONTRATOS.md.
        String email = tomador.getUsername();
        String nome = derivarNomePlaceholder(email, tomador.getId());
        RequisicaoEnvioAssinatura req =
                new RequisicaoEnvioAssinatura(contrato.getId(), versao.getId(), email, nome, idempotencyKey);
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

        eventPublisher.publishEvent(new AssinaturaEnviadaEvent(
                contrato.getId(),
                contrato.getPropostaId(),
                contrato.getTomadorId(),
                versao.getId(),
                envelope.getId(),
                envelope.getIdEnvelopeExterno(),
                PROVIDER_NAME,
                hashPdf));

        log.info(
                "Contrato enviado para assinatura contratoId={} envelopeId={} idEnvelopeExterno={}",
                contratoId,
                envelope.getId(),
                envelope.getIdEnvelopeExterno());
        return envelope;
    }

    /**
     * Placeholder seguro para nome do signatario quando onboarding ainda nao expoe o nome real.
     * Garante {@code nome != email} (exigencia do Clicksign + Lei 14.063 — assinatura eletronica
     * requer identificacao distinta do meio de contato).
     */
    private String derivarNomePlaceholder(String email, UUID tomadorId) {
        int at = email.indexOf('@');
        String prefixo = (at > 0 ? email.substring(0, at) : email);
        return "Tomador " + prefixo + " #" + tomadorId.toString().substring(0, 8);
    }
}
