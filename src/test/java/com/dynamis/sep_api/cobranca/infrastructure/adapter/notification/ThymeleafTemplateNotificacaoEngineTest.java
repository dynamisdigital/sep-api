package com.dynamis.sep_api.cobranca.infrastructure.adapter.notification;

import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThymeleafTemplateNotificacaoEngineTest {

    private final ThymeleafTemplateNotificacaoEngine engine = new ThymeleafTemplateNotificacaoEngine();

    @Test
    void renderiza_email_cobranca_amigavel_com_variaveis() {
        String html = engine.renderizar(
                CanalNotificacao.EMAIL,
                "cobranca-amigavel",
                Map.of("numeroParcela", 3, "dataVencimento", "10/06/2026", "valor", "R$ 250,00"));

        assertThat(html).contains("parcela", "<strong>3</strong>", "10/06/2026", "R$ 250,00");
    }

    @Test
    void renderiza_sms_lembrete_com_variaveis() {
        String txt = engine.renderizar(
                CanalNotificacao.SMS,
                "cobranca-lembrete",
                Map.of("numeroParcela", 5, "dataVencimento", "15/07/2026", "valor", "R$ 100,00"));

        assertThat(txt).contains("SEP", "5", "15/07/2026", "R$ 100,00");
        assertThat(txt).doesNotContain("<");
    }

    @Test
    void renderiza_email_cobranca_firme_com_diasAtraso() {
        String html = engine.renderizar(
                CanalNotificacao.EMAIL, "cobranca-firme", Map.of("numeroParcela", 1, "diasAtraso", 15));

        assertThat(html).contains("15", "atraso");
    }

    @Test
    void templateInexistente_lancaExcecao() {
        assertThatThrownBy(() -> engine.renderizar(CanalNotificacao.EMAIL, "nao-existe", Map.of()))
                .isInstanceOf(TemplateNotificacaoException.class)
                .hasMessageContaining("template");
    }

    @Test
    void variaveis_null_naoQuebra() {
        // template precisa renderizar mesmo sem variaveis (Thymeleaf substitui por placeholder/falsy)
        String html = engine.renderizar(CanalNotificacao.EMAIL, "cobranca-amigavel", Map.of());

        assertThat(html).contains("parcela");
    }
}
