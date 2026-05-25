package com.dynamis.sep_api.cobranca.infrastructure.adapter.notification;

import com.dynamis.sep_api.cobranca.application.port.out.dto.Notificacao;
import com.dynamis.sep_api.cobranca.application.port.out.dto.ResultadoNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogNotificationProviderTest {

    private final LogNotificationProvider provider = new LogNotificationProvider();

    @Test
    void enviar_email_retornaSucesso() {
        Notificacao n = new Notificacao(
                CanalNotificacao.EMAIL, "joao@cliente.com", "cobranca-amigavel", Map.of("numeroParcela", 1), "corr-1");

        ResultadoNotificacao r = provider.enviar(n);

        assertThat(r.status()).isEqualTo(StatusEventoCobranca.SUCESSO);
        assertThat(r.providerNome()).isEqualTo("log");
    }

    @Test
    void enviar_sms_retornaSucesso() {
        Notificacao n =
                new Notificacao(CanalNotificacao.SMS, "+5511999999999", "cobranca-lembrete", Map.of(), "corr-2");

        ResultadoNotificacao r = provider.enviar(n);

        assertThat(r.status()).isEqualTo(StatusEventoCobranca.SUCESSO);
    }

    @Test
    void suporta_todosCanais() {
        assertThat(provider.suporta(CanalNotificacao.EMAIL)).isTrue();
        assertThat(provider.suporta(CanalNotificacao.SMS)).isTrue();
    }

    @Test
    void nome_log() {
        assertThat(provider.nome()).isEqualTo("log");
    }
}
