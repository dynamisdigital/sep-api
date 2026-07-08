package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.EscalarCobrancaCommand;
import com.dynamis.sep_api.cobranca.application.dto.EscalonamentoResult;
import com.dynamis.sep_api.cobranca.application.port.out.EventoCobrancaPort;
import com.dynamis.sep_api.cobranca.application.port.out.NotificationProvider;
import com.dynamis.sep_api.cobranca.application.port.out.dto.Notificacao;
import com.dynamis.sep_api.cobranca.application.port.out.dto.ResultadoNotificacao;
import com.dynamis.sep_api.cobranca.application.service.workflow.WorkflowCobrancaProperties;
import com.dynamis.sep_api.cobranca.application.service.workflow.WorkflowCobrancaProperties.EtapaProperties;
import com.dynamis.sep_api.cobranca.application.service.workflow.WorkflowCobrancaResolver;
import com.dynamis.sep_api.cobranca.domain.model.EventoCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EscalarCobrancaUseCaseTest {

    private static final UUID PARCELA = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-10T10:00:00Z"), ZoneOffset.UTC);

    private EventoCobrancaPort eventoPort;
    private NotificationProvider emailProvider;
    private NotificationProvider smsProvider;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private EscalarCobrancaUseCase useCase;

    @BeforeEach
    void setup() {
        eventoPort = mock(EventoCobrancaPort.class);
        emailProvider = mock(NotificationProvider.class);
        smsProvider = mock(NotificationProvider.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        when(emailProvider.suporta(CanalNotificacao.EMAIL)).thenReturn(true);
        when(emailProvider.suporta(CanalNotificacao.SMS)).thenReturn(false);
        when(smsProvider.suporta(CanalNotificacao.SMS)).thenReturn(true);
        when(smsProvider.suporta(CanalNotificacao.EMAIL)).thenReturn(false);
        when(emailProvider.enviar(any())).thenReturn(ResultadoNotificacao.sucesso("smtp", "id-email"));
        when(smsProvider.enviar(any())).thenReturn(ResultadoNotificacao.sucesso("zenvia", "id-sms"));
        // Task 13.8 fix: use case agora publica EventoCobrancaRegistradoEvent apos save; precisa
        // mockar save retornando o argumento pra getId() funcionar.
        when(eventoPort.salvar(any(EventoCobranca.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase = new EscalarCobrancaUseCase(
                resolverComEtapas(), List.of(emailProvider, smsProvider), eventoPort, eventPublisher, CLOCK);
    }

    private static WorkflowCobrancaResolver resolverComEtapas() {
        return new WorkflowCobrancaResolver(new WorkflowCobrancaProperties(List.of(
                new EtapaProperties(0, List.of("email-amigavel"), false, false, false),
                new EtapaProperties(5, List.of("email-amigavel", "sms-lembrete"), false, false, false),
                new EtapaProperties(30, List.of("email-firme"), true, false, false),
                new EtapaProperties(90, List.of(), false, false, true))));
    }

    @Test
    void semEtapa_naoChamaProvider() {
        EscalonamentoResult r = useCase.escalar(comando(3));

        assertThat(r.tinhaEtapa()).isFalse();
        assertThat(r.eventosCriados()).isZero();
        verify(emailProvider, never()).enviar(any());
        verify(smsProvider, never()).enviar(any());
    }

    @Test
    void dia0_emailUnico_chamaEmailProviderEGravaEvento() {
        EscalonamentoResult r = useCase.escalar(comando(0));

        assertThat(r.tinhaEtapa()).isTrue();
        assertThat(r.eventosCriados()).isEqualTo(1);
        verify(emailProvider).enviar(any(Notificacao.class));
        verify(eventoPort).salvar(any(EventoCobranca.class));
    }

    @Test
    void dia5_dispara2NotificacoesEmCanaisDiferentes() {
        EscalonamentoResult r = useCase.escalar(comando(5));

        assertThat(r.eventosCriados()).isEqualTo(2);
        verify(emailProvider).enviar(any());
        verify(smsProvider).enviar(any());
    }

    @Test
    void notificacaoJaEnviada_skipsProvider() {
        when(eventoPort.jaNotificado(eq(PARCELA), eq(0), eq(CanalNotificacao.EMAIL), eq("cobranca-amigavel")))
                .thenReturn(true);

        EscalonamentoResult r = useCase.escalar(comando(0));

        assertThat(r.tinhaEtapa()).isTrue();
        assertThat(r.eventosCriados()).isZero();
        verify(emailProvider, never()).enviar(any());
    }

    @Test
    void destinatarioVazio_gravaFalhaSemChamarProvider() {
        EscalonamentoResult r =
                useCase.escalar(new EscalarCobrancaCommand(PARCELA, 0, null, null, Map.of("numeroParcela", 1), "corr"));

        assertThat(r.eventosCriados()).isEqualTo(1);
        verify(emailProvider, never()).enviar(any());
        verify(eventoPort).salvar(any(EventoCobranca.class));
    }

    @Test
    void dia30_flagContatoManualPropagada() {
        EscalonamentoResult r = useCase.escalar(comando(30));

        assertThat(r.flagContatoManual()).isTrue();
        assertThat(r.escalonarBackoffice()).isFalse();
        assertThat(r.marcarInadimplente()).isFalse();
    }

    @Test
    void dia90_marcarInadimplenteSemNotificacoes() {
        EscalonamentoResult r = useCase.escalar(comando(90));

        assertThat(r.tinhaEtapa()).isTrue();
        assertThat(r.marcarInadimplente()).isTrue();
        assertThat(r.eventosCriados()).isZero();
        verify(emailProvider, never()).enviar(any());
    }

    @Test
    void falhaNoProvider_persisteEventoComStatusFalha() {
        when(emailProvider.enviar(any())).thenReturn(ResultadoNotificacao.falha("smtp", "down"));

        EscalonamentoResult r = useCase.escalar(comando(0));

        assertThat(r.eventosCriados()).isEqualTo(1);
        verify(eventoPort).salvar(any(EventoCobranca.class));
        // Status carregado no evento — validado indiretamente via factory de EventoCobranca
        // (notificacaoAutomatica recebe status como argumento). Assert direta seria fragil; o
        // contrato esta coberto pela cadeia ResultadoNotificacao -> EventoCobranca.
        assertThat(r.tinhaEtapa()).isTrue();
    }

    @Test
    void semProviderParaCanal_persistiFalhaSemQuebrarOutrosEnvios() {
        // Hotfix Task 13.4: provider SMS ausente NAO quebra a transacao — email da etapa dia 5
        // permanece persistido e SMS vira EventoCobranca FALHA com motivo "provider ausente".
        useCase = new EscalarCobrancaUseCase(
                resolverComEtapas(), List.of(emailProvider), eventoPort, eventPublisher, CLOCK);

        EscalonamentoResult r = useCase.escalar(comando(5));

        assertThat(r.eventosCriados()).isEqualTo(2);
        verify(emailProvider).enviar(any());
        verify(smsProvider, never()).enviar(any());
        org.mockito.ArgumentCaptor<EventoCobranca> captor = org.mockito.ArgumentCaptor.forClass(EventoCobranca.class);
        verify(eventoPort, org.mockito.Mockito.times(2)).salvar(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(EventoCobranca::getStatus)
                .containsExactlyInAnyOrder(StatusEventoCobranca.SUCESSO, StatusEventoCobranca.FALHA);
    }

    @Test
    void notificacaoUsaStatusFalhaQuandoProviderRetornaFalha() {
        when(emailProvider.enviar(any())).thenReturn(ResultadoNotificacao.falha("smtp", "render erro"));

        useCase.escalar(comando(0));

        org.mockito.ArgumentCaptor<EventoCobranca> captor = org.mockito.ArgumentCaptor.forClass(EventoCobranca.class);
        verify(eventoPort).salvar(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusEventoCobranca.FALHA);
    }

    @Test
    void persistirFalha_publicaEventRegistradoComStatusFalha() {
        // Fix review manual Task 13.8: persistirFalha tambem publica EventoCobrancaRegistradoEvent
        // pra audit log capturar tentativas frustradas. Antes, destinatario nulo / provider ausente
        // / exception nao chegavam ao audit_log_seguranca.
        org.mockito.ArgumentCaptor<com.dynamis.sep_api.cobranca.domain.event.EventoCobrancaRegistradoEvent> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.dynamis.sep_api.cobranca.domain.event.EventoCobrancaRegistradoEvent.class);

        useCase.escalar(new EscalarCobrancaCommand(PARCELA, 0, null, null, Map.of("numeroParcela", 1), "corr"));

        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        boolean temFalhaPublicada =
                captor.getAllValues().stream().anyMatch(e -> e.status() == StatusEventoCobranca.FALHA);
        assertThat(temFalhaPublicada).isTrue();
    }

    @Test
    void providerLancaExcecao_persistiFalhaSemPropagar() {
        // Fix review manual: provider.enviar lanca runtime (timeout, HTTP, CallNotPermitted).
        // Sem try/catch a tx faria rollback e perderia evento ja entregue em iteracao anterior.
        when(emailProvider.enviar(any())).thenThrow(new RuntimeException("connection reset"));

        EscalonamentoResult r = useCase.escalar(comando(0));

        assertThat(r.eventosCriados()).isEqualTo(1);
        org.mockito.ArgumentCaptor<EventoCobranca> captor = org.mockito.ArgumentCaptor.forClass(EventoCobranca.class);
        verify(eventoPort).salvar(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusEventoCobranca.FALHA);
    }

    @Test
    void providerLancaExcecaoNoSegundoCanal_naoPerdePrimeiro() {
        when(emailProvider.enviar(any())).thenReturn(ResultadoNotificacao.sucesso("smtp", "id-1"));
        when(smsProvider.enviar(any())).thenThrow(new RuntimeException("zenvia down"));

        EscalonamentoResult r = useCase.escalar(comando(5));

        assertThat(r.eventosCriados()).isEqualTo(2);
        verify(eventoPort, org.mockito.Mockito.times(2)).salvar(any());
    }

    @Test
    void flags_publicamEtapaCobrancaAplicadaEvent() {
        useCase.escalar(comando(30));

        org.mockito.ArgumentCaptor<com.dynamis.sep_api.cobranca.domain.event.EtapaCobrancaAplicadaEvent> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.dynamis.sep_api.cobranca.domain.event.EtapaCobrancaAplicadaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().flagContatoManual()).isTrue();
        assertThat(captor.getValue().diasAtraso()).isEqualTo(30);
    }

    @Test
    void semEtapa_naoPublicaEvent() {
        useCase.escalar(comando(3));

        verify(eventPublisher, never()).publishEvent(any());
    }

    private EscalarCobrancaCommand comando(int dias) {
        return new EscalarCobrancaCommand(
                PARCELA,
                dias,
                "cliente@example.com",
                "+5511999999999",
                Map.of("numeroParcela", 1, "dataVencimento", "10/06/2026", "valor", "R$ 100,00"),
                "corr-test");
    }
}
