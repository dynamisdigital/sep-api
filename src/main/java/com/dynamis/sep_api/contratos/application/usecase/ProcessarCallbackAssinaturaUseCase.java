package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.application.port.out.DocumentoAssinadoStorage;
import com.dynamis.sep_api.contratos.application.service.HashContratoService;
import com.dynamis.sep_api.contratos.domain.event.AssinaturaVisualizadaEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoAssinadoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoRecusadoEvent;
import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
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
import org.springframework.dao.DataIntegrityViolationException;
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

        // Fix C2 review Task 11.5: lock global ordering — contrato sempre ANTES de envelope.
        // Descobre contratoId via leitura sem lock; depois lock contrato; depois re-lock envelope.
        // Callbacks concorrentes de envelopes diferentes que apontam pro mesmo contrato passam a
        // serializar via lock do contrato em vez de deadlock cross-resource.
        EnvelopeAssinatura snapshot = envelopeRepository
                .findByProviderAndIdEnvelopeExterno(callback.provider(), callback.idEnvelopeExterno())
                .orElseThrow(() -> new ContratoEstadoInvalidoException(
                        "callback.envelopeAusente:" + callback.provider() + "/" + callback.idEnvelopeExterno(), null));
        Contrato contrato = contratoLoader.carregarComLock(snapshot.getContratoId());
        EnvelopeAssinatura envelope = envelopeRepository
                .findByProviderAndIdEnvelopeExternoForUpdate(callback.provider(), callback.idEnvelopeExterno())
                .orElseThrow(() -> new ContratoEstadoInvalidoException(
                        "callback.envelopeAusente:" + callback.provider() + "/" + callback.idEnvelopeExterno(), null));

        // Fix M2 review Task 11.5: dedup em duas camadas. existsBy = fast path; save+flush com
        // catch DataIntegrityViolationException defende contra race se lock pessimista falhar ou
        // expirar (UNIQUE em (envelope_id, id_evento_externo) na V23). Padrao identico a
        // RegistrarWebhookEventUseCase (Sprint 4) e RegistrarAceiteUseCase (Sprint 10).
        if (eventoRepository.existsByEnvelopeIdAndIdEventoExterno(envelope.getId(), callback.idEventoExterno())) {
            log.info(
                    "Callback duplicado ignorado envelopeId={} idEventoExterno={}",
                    envelope.getId(),
                    callback.idEventoExterno());
            return false;
        }

        try {
            eventoRepository.saveAndFlush(EventoAssinatura.criar(
                    envelope.getId(),
                    callback.idEventoExterno(),
                    callback.status(),
                    callback.payloadResumo(),
                    callback.dataEvento()));
        } catch (DataIntegrityViolationException ex) {
            log.info(
                    "Callback duplicado detectado em save envelopeId={} idEventoExterno={}",
                    envelope.getId(),
                    callback.idEventoExterno());
            return false;
        }

        switch (callback.status()) {
            case ASSINADO -> finalizarComoAssinado(envelope, contrato, callback);
            case RECUSADO -> finalizarComoRecusado(envelope, contrato, callback);
            case EXPIRADO -> envelope.marcarExpirado(callback.dataEvento());
            case VISUALIZADO -> {
                envelope.marcarVisualizado(callback.dataEvento());
                eventPublisher.publishEvent(new AssinaturaVisualizadaEvent(
                        contrato.getId(),
                        contrato.getTomadorId(),
                        envelope.getId(),
                        envelope.getProvider(),
                        callback.dataEvento()));
            }
            case ENVIADO, RASCUNHO -> log.debug(
                    "Callback informativo ignorado status={} envelopeId={}", callback.status(), envelope.getId());
                // Fix M3 review Task 11.5: default defende contra adicao futura de StatusEnvelope sem
                // update aqui — silent ignore mascararia o evento e o envelope ficaria fora de sync.
            default -> log.warn(
                    "Callback com status desconhecido status={} envelopeId={}", callback.status(), envelope.getId());
        }
        return true;
    }

    private void finalizarComoAssinado(EnvelopeAssinatura envelope, Contrato contrato, CallbackAssinatura callback) {
        byte[] pdfAssinado = provider.baixarDocumentoAssinado(envelope.getIdEnvelopeExterno());
        String hash = hashService.calcular(pdfAssinado);
        String pathStorage = storage.salvar(pdfAssinado);
        boolean documentoPersistido = false;
        try {
            DocumentoAssinado documento = DocumentoAssinado.criar(
                    envelope.getId(), hash, callback.dataEvento(), callback.selo(), pathStorage);
            documento = documentoRepository.save(documento);
            documentoPersistido = true;
            envelope.marcarAssinado(callback.dataEvento());
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
        } finally {
            // Fix C3 review Task 11.5: compensa blob orfao quando documento nao persistiu
            // (constraint violation, hash invalido). Storage.salvar ja commitou (impl inline
            // grava por save() do repository); compensating delete previne orphan rows.
            if (!documentoPersistido) {
                try {
                    storage.deletar(pathStorage);
                } catch (RuntimeException ex) {
                    log.warn(
                            "Falha ao compensar blob orfao apos erro no DocumentoAssinado pathStorage={}: {}",
                            pathStorage,
                            ex.getMessage());
                }
            }
        }
    }

    private void finalizarComoRecusado(EnvelopeAssinatura envelope, Contrato contrato, CallbackAssinatura callback) {
        envelope.marcarRecusado(callback.dataEvento());
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
