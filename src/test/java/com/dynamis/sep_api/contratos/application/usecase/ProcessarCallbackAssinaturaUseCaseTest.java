package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.application.port.out.DocumentoAssinadoStorage;
import com.dynamis.sep_api.contratos.application.service.HashContratoService;
import com.dynamis.sep_api.contratos.application.usecase.ProcessarCallbackAssinaturaUseCase.CallbackAssinatura;
import com.dynamis.sep_api.contratos.domain.event.ContratoAssinadoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoRecusadoEvent;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.DocumentoAssinado;
import com.dynamis.sep_api.contratos.domain.model.EnvelopeAssinatura;
import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.DocumentoAssinadoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EnvelopeAssinaturaRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EventoAssinaturaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessarCallbackAssinaturaUseCaseTest {

    private static final String HASH = "0".repeat(64);
    private static final OffsetDateTime AGORA = OffsetDateTime.now();

    private EnvelopeAssinaturaRepository envelopeRepository;
    private EventoAssinaturaRepository eventoRepository;
    private DocumentoAssinadoRepository documentoRepository;
    private ContratoLoaderService loader;
    private AssinaturaDigitalProvider provider;
    private DocumentoAssinadoStorage storage;
    private HashContratoService hashService;
    private ApplicationEventPublisher eventPublisher;
    private ProcessarCallbackAssinaturaUseCase useCase;

    private Contrato contrato;
    private EnvelopeAssinatura envelope;

    @BeforeEach
    void setUp() {
        envelopeRepository = mock(EnvelopeAssinaturaRepository.class);
        eventoRepository = mock(EventoAssinaturaRepository.class);
        documentoRepository = mock(DocumentoAssinadoRepository.class);
        loader = mock(ContratoLoaderService.class);
        provider = mock(AssinaturaDigitalProvider.class);
        storage = mock(DocumentoAssinadoStorage.class);
        hashService = new HashContratoService();
        eventPublisher = mock(ApplicationEventPublisher.class);

        useCase = new ProcessarCallbackAssinaturaUseCase(
                envelopeRepository,
                eventoRepository,
                documentoRepository,
                loader,
                provider,
                storage,
                hashService,
                eventPublisher);

        contrato = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.CCB);
        contrato.adicionarVersao("c", HASH);
        contrato.marcarAceito();
        contrato.marcarEmAssinatura();
        envelope = EnvelopeAssinatura.criar(
                contrato.getId(),
                contrato.versaoVigente().orElseThrow().getId(),
                "clicksign",
                "ext-1",
                contrato.getId() + ":v1",
                HASH,
                AGORA);

        when(envelopeRepository.findByProviderAndIdEnvelopeExternoForUpdate("clicksign", "ext-1"))
                .thenReturn(Optional.of(envelope));
        when(loader.carregarComLock(contrato.getId())).thenReturn(contrato);
    }

    @Test
    void executar_assinado_baixaPdfEPublicaEvento() {
        byte[] pdf = "%PDF assinado".getBytes();
        when(provider.baixarDocumentoAssinado("ext-1")).thenReturn(pdf);
        when(storage.salvar(pdf)).thenReturn("path-1");
        when(documentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventoRepository.existsByEnvelopeIdAndIdEventoExterno(any(), any()))
                .thenReturn(false);

        boolean processado = useCase.executar(
                new CallbackAssinatura("clicksign", "ext-1", "evt-1", StatusEnvelope.ASSINADO, "ok", "selo-x", AGORA));

        assertThat(processado).isTrue();
        assertThat(envelope.getStatus()).isEqualTo(StatusEnvelope.ASSINADO);
        assertThat(contrato.getStatus()).isEqualTo(StatusFormalizacao.ASSINADO);
        verify(eventPublisher).publishEvent(isA(ContratoAssinadoEvent.class));
        verify(documentoRepository).save(any(DocumentoAssinado.class));
    }

    @Test
    void executar_eventoDuplicado_noop() {
        when(eventoRepository.existsByEnvelopeIdAndIdEventoExterno(any(), any()))
                .thenReturn(true);

        boolean processado = useCase.executar(
                new CallbackAssinatura("clicksign", "ext-1", "evt-1", StatusEnvelope.ASSINADO, "ok", null, AGORA));

        assertThat(processado).isFalse();
        verify(provider, never()).baixarDocumentoAssinado(any());
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void executar_recusado_transicionaEPublicaEvento() {
        when(eventoRepository.existsByEnvelopeIdAndIdEventoExterno(any(), any()))
                .thenReturn(false);

        useCase.executar(
                new CallbackAssinatura("clicksign", "ext-1", "evt-1", StatusEnvelope.RECUSADO, "refused", null, AGORA));

        assertThat(envelope.getStatus()).isEqualTo(StatusEnvelope.RECUSADO);
        assertThat(contrato.getStatus()).isEqualTo(StatusFormalizacao.RECUSADO);
        verify(eventPublisher).publishEvent(isA(ContratoRecusadoEvent.class));
    }

    @Test
    void executar_visualizado_naoTransicionaContrato() {
        when(eventoRepository.existsByEnvelopeIdAndIdEventoExterno(any(), any()))
                .thenReturn(false);

        useCase.executar(new CallbackAssinatura(
                "clicksign", "ext-1", "evt-1", StatusEnvelope.VISUALIZADO, "viewed", null, AGORA));

        assertThat(envelope.getStatus()).isEqualTo(StatusEnvelope.VISUALIZADO);
        assertThat(contrato.getStatus()).isEqualTo(StatusFormalizacao.EM_ASSINATURA);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_expirado_transicionaSomenteEnvelope() {
        when(eventoRepository.existsByEnvelopeIdAndIdEventoExterno(any(), any()))
                .thenReturn(false);

        useCase.executar(
                new CallbackAssinatura("clicksign", "ext-1", "evt-1", StatusEnvelope.EXPIRADO, "expired", null, AGORA));

        assertThat(envelope.getStatus()).isEqualTo(StatusEnvelope.EXPIRADO);
        assertThat(contrato.getStatus()).isEqualTo(StatusFormalizacao.EM_ASSINATURA);
    }
}
