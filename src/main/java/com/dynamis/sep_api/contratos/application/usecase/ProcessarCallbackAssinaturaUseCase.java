package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.application.port.out.DocumentoAssinadoStorage;
import com.dynamis.sep_api.contratos.application.service.HashContratoService;
import com.dynamis.sep_api.contratos.domain.event.ContratoAssinadoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoRecusadoEvent;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.DocumentoAssinado;
import com.dynamis.sep_api.contratos.domain.model.EnvelopeAssinatura;
import com.dynamis.sep_api.contratos.domain.model.EventoAssinatura;
import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import com.dynamis.sep_api.contratos.infrastructure.persistence.DocumentoAssinadoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EnvelopeAssinaturaRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EventoAssinaturaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Caso de uso: processa callback do provider de assinatura (Sprint 11 Task 11.5). Invocado pelo
 * webhook (Task 11.6) apos validacao HMAC.
 *
 * <p>Fluxo idempotente:
 * <ol>
 *   <li>Localiza envelope por {@code (provider, idEnvelopeExterno)} com lock pessimista.
 *   <li>Dedup por {@code (envelopeId, idEventoExterno)} — evento ja processado eh no-op.
 *   <li>Cria {@link EventoAssinatura} sanitizado (payload truncado a 1000 chars).
 *   <li>ASSINADO: baixa PDF assinado via provider, calcula hash, persiste via {@link
 *       DocumentoAssinadoStorage}, cria {@link DocumentoAssinado}, transiciona envelope+contrato
 *       e publica {@link ContratoAssinadoEvent}.
 *   <li>RECUSADO: transiciona envelope+contrato; publica {@link ContratoRecusadoEvent}.
 *   <li>EXPIRADO/VISUALIZADO: transicao no envelope; contrato preservado em {@code EM_ASSINATURA}.
 * </ol>
 */
@Service
public class ProcessarCallbackAssinaturaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarCallbackAssinaturaUseCase.class);

    private final EnvelopeAssinaturaRepository envelopeRepository;
    private final EventoAssinaturaRepository eventoRepository;
    private final DocumentoAssinadoRepository documentoRepository;
    private final ContratoLoaderService contratoLoader;
    private final AssinaturaDigitalProvider provider;
    private final DocumentoAssinadoStorage storage;
    private final HashContratoService hashService;
    private final ApplicationEventPublisher eventPublisher;

    public ProcessarCallbackAssinaturaUseCase(
            EnvelopeAssinaturaRepository envelopeRepository,
            EventoAssinaturaRepository eventoRepository,
            DocumentoAssinadoRepository documentoRepository,
            ContratoLoaderService contratoLoader,
            AssinaturaDigitalProvider provider,
            DocumentoAssinadoStorage storage,
            HashContratoService hashService,
            ApplicationEventPublisher eventPublisher) {
        this.envelopeRepository = envelopeRepository;
        this.eventoRepository = eventoRepository;
        this.documentoRepository = documentoRepository;
        this.contratoLoader = contratoLoader;
        this.provider = provider;
        this.storage = storage;
        this.hashService = hashService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Processa um callback do provider. Retorna {@code true} se houve efeito; {@code false} se
     * evento era duplicado (idempotencia silenciosa).
     */
    @Transactional
    public boolean executar(CallbackAssinatura callback) {
        Objects.requireNonNull(callback, "callback obrigatorio");
        EnvelopeAssinatura envelope = envelopeRepository
                .findByProviderAndIdEnvelopeExternoForUpdate(callback.provider(), callback.idEnvelopeExterno())
                .orElseThrow(() -> new IllegalStateException(
                        "Envelope nao encontrado: " + callback.provider() + "/" + callback.idEnvelopeExterno()));

        if (eventoRepository.existsByEnvelopeIdAndIdEventoExterno(envelope.getId(), callback.idEventoExterno())) {
            log.info(
                    "Callback duplicado ignorado envelopeId={} idEventoExterno={}",
                    envelope.getId(),
                    callback.idEventoExterno());
            return false;
        }

        eventoRepository.save(EventoAssinatura.criar(
                envelope.getId(),
                callback.idEventoExterno(),
                callback.status(),
                callback.payloadResumo(),
                callback.dataEvento()));

        switch (callback.status()) {
            case ASSINADO -> finalizarComoAssinado(envelope, callback);
            case RECUSADO -> finalizarComoRecusado(envelope, callback);
            case EXPIRADO -> envelope.marcarExpirado(callback.dataEvento());
            case VISUALIZADO -> envelope.marcarVisualizado(callback.dataEvento());
            case ENVIADO, RASCUNHO -> log.debug(
                    "Callback informativo ignorado status={} envelopeId={}", callback.status(), envelope.getId());
        }
        return true;
    }

    private void finalizarComoAssinado(EnvelopeAssinatura envelope, CallbackAssinatura callback) {
        byte[] pdfAssinado = provider.baixarDocumentoAssinado(envelope.getIdEnvelopeExterno());
        String hash = hashService.calcular(pdfAssinado);
        String pathStorage = storage.salvar(pdfAssinado);

        DocumentoAssinado documento =
                DocumentoAssinado.criar(envelope.getId(), hash, callback.dataEvento(), callback.selo(), pathStorage);
        documento = documentoRepository.save(documento);
        envelope.marcarAssinado(callback.dataEvento());

        Contrato contrato = contratoLoader.carregarComLock(envelope.getContratoId());
        contrato.marcarAssinado();

        eventPublisher.publishEvent(new ContratoAssinadoEvent(
                contrato.getId(),
                contrato.getPropostaId(),
                contrato.getTomadorId(),
                envelope.getVersaoId(),
                envelope.getId(),
                documento.getId(),
                hash));
        log.info(
                "Contrato assinado contratoId={} envelopeId={} documentoId={}",
                contrato.getId(),
                envelope.getId(),
                documento.getId());
    }

    private void finalizarComoRecusado(EnvelopeAssinatura envelope, CallbackAssinatura callback) {
        envelope.marcarRecusado(callback.dataEvento());
        Contrato contrato = contratoLoader.carregarComLock(envelope.getContratoId());
        contrato.marcarRecusado();

        eventPublisher.publishEvent(new ContratoRecusadoEvent(
                contrato.getId(),
                contrato.getPropostaId(),
                contrato.getTomadorId(),
                envelope.getVersaoId(),
                envelope.getId()));
        log.info("Contrato recusado contratoId={} envelopeId={}", contrato.getId(), envelope.getId());
    }

    /**
     * Comando do callback. Webhook (Task 11.6) traduz payload do provider para este record antes
     * de chamar {@link #executar(CallbackAssinatura)}.
     */
    public record CallbackAssinatura(
            String provider,
            String idEnvelopeExterno,
            String idEventoExterno,
            StatusEnvelope status,
            String payloadResumo,
            String selo,
            OffsetDateTime dataEvento) {

        public CallbackAssinatura {
            Objects.requireNonNull(provider, "provider obrigatorio");
            Objects.requireNonNull(idEnvelopeExterno, "idEnvelopeExterno obrigatorio");
            Objects.requireNonNull(idEventoExterno, "idEventoExterno obrigatorio");
            Objects.requireNonNull(status, "status obrigatorio");
            Objects.requireNonNull(dataEvento, "dataEvento obrigatoria");
        }
    }
}
