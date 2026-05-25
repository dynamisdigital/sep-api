package com.dynamis.sep_api.cobranca.infrastructure.adapter.notification;

import com.dynamis.sep_api.cobranca.application.port.out.TemplateNotificacaoEngine;
import com.dynamis.sep_api.cobranca.application.port.out.dto.Notificacao;
import com.dynamis.sep_api.cobranca.application.port.out.dto.ResultadoNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpNotificationProviderTest {

    private JavaMailSender mailSender;
    private TemplateNotificacaoEngine engine;
    private SmtpNotificationProvider provider;

    @BeforeEach
    void setup() {
        mailSender = mock(JavaMailSender.class);
        engine = mock(TemplateNotificacaoEngine.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        when(engine.renderizar(eq(CanalNotificacao.EMAIL), eq("cobranca-amigavel"), any()))
                .thenReturn("<html><body>oi</body></html>");

        provider = new SmtpNotificationProvider(
                mailSender,
                engine,
                new NotificacaoProperties(
                        "smtp-zenvia", "sep@empresa.com", new NotificacaoProperties.Zenvia(null, null, "SEP", 5000)));
    }

    @Test
    void enviar_email_valido_chamaSendERetornaSucesso() {
        Notificacao n = new Notificacao(
                CanalNotificacao.EMAIL, "cliente@example.com", "cobranca-amigavel", Map.of("x", 1), "c-1");

        ResultadoNotificacao r = provider.enviar(n);

        assertThat(r.status()).isEqualTo(StatusEventoCobranca.SUCESSO);
        assertThat(r.providerNome()).isEqualTo("smtp");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void enviar_canalSms_retornaFalha() {
        Notificacao n = new Notificacao(CanalNotificacao.SMS, "+5511999999999", "x", Map.of(), null);

        ResultadoNotificacao r = provider.enviar(n);

        assertThat(r.status()).isEqualTo(StatusEventoCobranca.FALHA);
        assertThat(r.mensagemTecnica()).contains("canal");
    }

    @Test
    void enviar_destinatarioInvalido_retornaFalha() {
        Notificacao n = new Notificacao(CanalNotificacao.EMAIL, "nao-eh-email", "cobranca-amigavel", Map.of(), null);

        ResultadoNotificacao r = provider.enviar(n);

        assertThat(r.status()).isEqualTo(StatusEventoCobranca.FALHA);
        assertThat(r.mensagemTecnica()).contains("destinatario");
    }

    @Test
    void enviar_mailSenderFalha_retornaFalha() {
        Notificacao n =
                new Notificacao(CanalNotificacao.EMAIL, "cliente@example.com", "cobranca-amigavel", Map.of(), null);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        ResultadoNotificacao r = provider.enviar(n);

        assertThat(r.status()).isEqualTo(StatusEventoCobranca.FALHA);
        assertThat(r.mensagemTecnica()).contains("smtp");
    }

    @Test
    void enviar_renderFalha_retornaFalha() {
        when(engine.renderizar(eq(CanalNotificacao.EMAIL), eq("cobranca-amigavel"), any()))
                .thenThrow(new TemplateNotificacaoException("template invalido"));
        Notificacao n =
                new Notificacao(CanalNotificacao.EMAIL, "cliente@example.com", "cobranca-amigavel", Map.of(), null);

        ResultadoNotificacao r = provider.enviar(n);

        assertThat(r.status()).isEqualTo(StatusEventoCobranca.FALHA);
        assertThat(r.mensagemTecnica()).contains("render");
    }

    @Test
    void suporta_apenasEmail() {
        assertThat(provider.suporta(CanalNotificacao.EMAIL)).isTrue();
        assertThat(provider.suporta(CanalNotificacao.SMS)).isFalse();
    }

    @Test
    void nome_smtp() {
        assertThat(provider.nome()).isEqualTo("smtp");
    }
}
