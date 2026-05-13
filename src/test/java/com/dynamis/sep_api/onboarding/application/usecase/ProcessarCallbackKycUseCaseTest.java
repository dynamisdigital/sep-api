package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.event.OnboardingFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.model.ResultadoVerificacao;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinKycMapper;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ResultadoVerificacaoRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.application.usecase.RegistrarWebhookEventUseCase;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.domain.model.WebhookEventStatus;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessarCallbackKycUseCaseTest {

    private RegistrarWebhookEventUseCase registrarWebhookEventUseCase;
    private WebhookEventLogRepository webhookEventLogRepository;
    private SolicitacaoOnboardingRepository solicitacaoRepository;
    private ResultadoVerificacaoRepository resultadoRepository;
    private CelcoinKycMapper mapper;
    private ApplicationEventPublisher eventPublisher;
    private ProcessarCallbackKycUseCase useCase;

    private WebhookEventLog evento;
    private SolicitacaoOnboarding solicitacao;

    @BeforeEach
    void setup() {
        registrarWebhookEventUseCase = mock(RegistrarWebhookEventUseCase.class);
        webhookEventLogRepository = mock(WebhookEventLogRepository.class);
        solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        resultadoRepository = mock(ResultadoVerificacaoRepository.class);
        mapper = new CelcoinKycMapper() {};
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new ProcessarCallbackKycUseCase(
                registrarWebhookEventUseCase,
                webhookEventLogRepository,
                solicitacaoRepository,
                resultadoRepository,
                mapper,
                eventPublisher);

        evento = WebhookEventLog.registrar("celcoin-kyc", "callback", "idem-1", "sig", "{}");
        when(webhookEventLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(evento));

        solicitacao = SolicitacaoOnboarding.criar(
                UUID.randomUUID(), new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1));
        solicitacao.registrarDocumentoEnviado();
        solicitacao.marcarEmVerificacao("ext-1");
        when(solicitacaoRepository.findByIdVerificacaoExterna("ext-1")).thenReturn(Optional.of(solicitacao));
        when(solicitacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(resultadoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aPPROVEDFinalizaSolicitacaoPersisteResultadoEPublicaEvento() {
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        String payload = "{\"verification_id\":\"ext-1\",\"status\":\"APPROVED\"}";
        var callback = new ProcessarCallbackKycUseCase.CelcoinKycCallback("ext-1", "APPROVED", null);

        var resultado = useCase.executar("idem-1", "sig", payload, callback);

        assertThat(resultado.aceito()).isTrue();
        assertThat(resultado.duplicado()).isFalse();
        assertThat(solicitacao.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
        verify(resultadoRepository).save(any(ResultadoVerificacao.class));
        verify(eventPublisher).publishEvent(any(OnboardingFinalizadoEvent.class));
        assertThat(evento.getStatus()).isEqualTo(WebhookEventStatus.PROCESSADO);
    }

    @Test
    void rEJECTEDTransicionaParaREPROVADO() {
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        String payload = "{\"verification_id\":\"ext-1\",\"status\":\"REJECTED\",\"reason\":\"docs inconsistentes\"}";
        var callback = new ProcessarCallbackKycUseCase.CelcoinKycCallback("ext-1", "REJECTED", "docs inconsistentes");

        useCase.executar("idem-1", "sig", payload, callback);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusOnboarding.REPROVADO);
    }

    @Test
    void pENDINGFinalizaComoPENDENCIA() {
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        var callback = new ProcessarCallbackKycUseCase.CelcoinKycCallback("ext-1", "PENDING", null);

        useCase.executar("idem-1", "sig", "{}", callback);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusOnboarding.PENDENCIA);
    }

    @Test
    void pROCESSINGNaoFinalizaSolicitacaoMasMarcaEventoProcessado() {
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        var callback = new ProcessarCallbackKycUseCase.CelcoinKycCallback("ext-1", "PROCESSING", null);

        useCase.executar("idem-1", "sig", "{}", callback);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusOnboarding.EM_VERIFICACAO);
        verify(resultadoRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(OnboardingFinalizadoEvent.class));
        assertThat(evento.getStatus()).isEqualTo(WebhookEventStatus.PROCESSADO);
    }

    @Test
    void duplicadoIdempotenteCurtoCircuitoEMarcaResultadoDuplicado() {
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false);
        var callback = new ProcessarCallbackKycUseCase.CelcoinKycCallback("ext-1", "APPROVED", null);

        var resultado = useCase.executar("idem-1", "sig", "{}", callback);

        assertThat(resultado.duplicado()).isTrue();
        verify(solicitacaoRepository, never()).findByIdVerificacaoExterna(anyString());
        verify(resultadoRepository, never()).save(any());
    }

    @Test
    void solicitacaoNaoEncontradaMarcaEventoFalhouSemErro500() {
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(solicitacaoRepository.findByIdVerificacaoExterna(anyString())).thenReturn(Optional.empty());
        var callback = new ProcessarCallbackKycUseCase.CelcoinKycCallback("ext-naoexiste", "APPROVED", null);

        var resultado = useCase.executar("idem-1", "sig", "{}", callback);

        assertThat(resultado.aceito()).isTrue();
        assertThat(evento.getStatus()).isEqualTo(WebhookEventStatus.FALHOU);
        verify(resultadoRepository, never()).save(any());
    }

    @Test
    void callbackTardioMesmoStatusFinalMarcaProcessadoSemReescrever() {
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        // Solicitacao ja finalizada (status final + ResultadoVerificacao existente)
        solicitacao.finalizar(StatusOnboarding.APROVADO);
        when(resultadoRepository.findBySolicitacaoId(any()))
                .thenReturn(Optional.of(
                        ResultadoVerificacao.registrar(solicitacao.getId(), StatusOnboarding.APROVADO, null, "{}")));
        var callback = new ProcessarCallbackKycUseCase.CelcoinKycCallback("ext-1", "APPROVED", null);

        var resultado = useCase.executar("idem-tardio", "sig", "{}", callback);

        assertThat(resultado.aceito()).isTrue();
        assertThat(evento.getStatus()).isEqualTo(WebhookEventStatus.PROCESSADO);
        // NAO chama save novamente — resultado existente preservado.
        verify(resultadoRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(OnboardingFinalizadoEvent.class));
    }

    @Test
    void callbackConflitanteComResultadoExistenteMarcaFalhouSem500() {
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        solicitacao.finalizar(StatusOnboarding.APROVADO);
        when(resultadoRepository.findBySolicitacaoId(any()))
                .thenReturn(Optional.of(
                        ResultadoVerificacao.registrar(solicitacao.getId(), StatusOnboarding.APROVADO, null, "{}")));
        // Celcoin envia REJECTED quando ja havia APROVADO — conflito.
        var callback = new ProcessarCallbackKycUseCase.CelcoinKycCallback("ext-1", "REJECTED", "conflito");

        var resultado = useCase.executar("idem-conflito", "sig", "{}", callback);

        assertThat(resultado.aceito()).isTrue();
        assertThat(evento.getStatus()).isEqualTo(WebhookEventStatus.FALHOU);
        verify(resultadoRepository, never()).save(any());
    }

    @Test
    void payloadSemVerificationIdMarcaEventoFalhou() {
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        var callback = new ProcessarCallbackKycUseCase.CelcoinKycCallback(null, "APPROVED", null);

        var resultado = useCase.executar("idem-1", "sig", "{}", callback);

        assertThat(resultado.aceito()).isTrue();
        assertThat(evento.getStatus()).isEqualTo(WebhookEventStatus.FALHOU);
    }
}
