package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.event.KybFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaCNPJRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.onboarding.web.dto.CelcoinKybCallbackRequest;
import com.dynamis.sep_api.shared.application.usecase.RegistrarWebhookEventUseCase;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessarCallbackKybUseCaseTest {

    private static final String CNPJ_VALIDO = "11222333000181";

    private RegistrarWebhookEventUseCase registrarWebhookEventUseCase;
    private WebhookEventLogRepository webhookEventLogRepository;
    private SolicitacaoOnboardingRepository solicitacaoRepository;
    private KybEmpresaRepository kybRepository;
    private ConsultaCNPJRepository consultaCnpjRepository;
    private RepresentanteLegalRepository representanteRepository;
    private ApplicationEventPublisher eventPublisher;
    private ProcessarCallbackKybUseCase useCase;
    private WebhookEventLog evento;
    private SolicitacaoOnboarding solicitacao;
    private KybEmpresa kyb;

    @BeforeEach
    void setup() {
        registrarWebhookEventUseCase = mock(RegistrarWebhookEventUseCase.class);
        webhookEventLogRepository = mock(WebhookEventLogRepository.class);
        solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        kybRepository = mock(KybEmpresaRepository.class);
        consultaCnpjRepository = mock(ConsultaCNPJRepository.class);
        representanteRepository = mock(RepresentanteLegalRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new ProcessarCallbackKybUseCase(
                registrarWebhookEventUseCase,
                webhookEventLogRepository,
                solicitacaoRepository,
                kybRepository,
                consultaCnpjRepository,
                representanteRepository,
                eventPublisher);

        solicitacao = SolicitacaoOnboarding.criarEmpresa(UUID.randomUUID(), CNPJ_VALIDO, "ACME");
        solicitacao.registrarDocumentoEnviado();
        solicitacao.marcarEmVerificacao("ext-pre");
        kyb = KybEmpresa.criar(
                solicitacao.getId(), new Cnpj(CNPJ_VALIDO), "ACME", null, TipoSocietario.LTDA, PorteEmpresa.MEDIO);
        evento = WebhookEventLog.registrar("celcoin-kyb", "callback", "idem", "sig", "{}");

        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(webhookEventLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(evento));
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(kybRepository.findBySolicitacaoId(solicitacao.getId())).thenReturn(Optional.of(kyb));
        when(solicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kybRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CelcoinKybCallbackRequest payload(String situacao) {
        return new CelcoinKybCallbackRequest(
                solicitacao.getId().toString(),
                situacao,
                "ACME Industria LTDA",
                "ACME",
                null,
                null,
                null,
                null,
                List.of(new CelcoinKybCallbackRequest.RepresentanteCallback("Joao", "52998224725", "CEO")));
    }

    @Test
    void duplicadoIdempotenteRetornaResultadoSemReprocessar() {
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false);

        ProcessarCallbackKybUseCase.Resultado r = useCase.executar("idem", "sig", "{}", payload("ACTIVE"));

        assertThat(r.aceito()).isTrue();
        assertThat(r.duplicado()).isTrue();
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void situacaoAtivaFinalizaSolicitacaoAprovadoEPublicaEvento() {
        useCase.executar("idem-1", "sig", "{}", payload("ACTIVE"));

        assertThat(solicitacao.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
        ArgumentCaptor<KybFinalizadoEvent> evtCaptor = ArgumentCaptor.forClass(KybFinalizadoEvent.class);
        verify(eventPublisher).publishEvent(evtCaptor.capture());
        assertThat(evtCaptor.getValue().statusFinal()).isEqualTo(StatusOnboarding.APROVADO);
        verify(representanteRepository).save(any());
    }

    @Test
    void situacaoSuspensaFinalizaReprovadoSemRepresentantes() {
        useCase.executar("idem-2", "sig", "{}", payload("SUSPENDED"));

        assertThat(solicitacao.getStatus()).isEqualTo(StatusOnboarding.REPROVADO);
        verify(representanteRepository, never()).save(any());
    }

    @Test
    void solicitacaoJaFinalizadaTrataComoDuplicadoTardio() {
        SolicitacaoOnboarding ja = SolicitacaoOnboarding.criarEmpresa(UUID.randomUUID(), CNPJ_VALIDO, "ACME");
        ja.registrarDocumentoEnviado();
        ja.marcarEmVerificacao("ext");
        ja.finalizar(StatusOnboarding.APROVADO);
        when(solicitacaoRepository.findById(ja.getId())).thenReturn(Optional.of(ja));

        useCase.executar(
                "idem-tardio",
                "sig",
                "{}",
                new CelcoinKybCallbackRequest(
                        ja.getId().toString(), "ACTIVE", "ACME", null, null, null, null, null, null));

        verify(eventPublisher, never()).publishEvent(any());
        verify(consultaCnpjRepository, never()).save(any());
    }

    @Test
    void externalIdAusenteMarcaEventoFalhou() {
        useCase.executar(
                "idem-bad",
                "sig",
                "{}",
                new CelcoinKybCallbackRequest(null, "ACTIVE", null, null, null, null, null, null, null));

        verify(eventPublisher, never()).publishEvent(any());
    }
}
