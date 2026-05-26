package com.dynamis.sep_api.cobranca.application.service.workflow;

import com.dynamis.sep_api.cobranca.application.service.workflow.WorkflowCobrancaProperties.EtapaProperties;
import com.dynamis.sep_api.cobranca.application.service.workflow.WorkflowCobrancaResolver.EtapaWorkflow;
import com.dynamis.sep_api.cobranca.application.service.workflow.WorkflowCobrancaResolver.NotificacaoEtapa;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowCobrancaResolverTest {

    @Test
    void etapaParaDia_diaExato_retornaEtapa() {
        WorkflowCobrancaResolver resolver = new WorkflowCobrancaResolver(new WorkflowCobrancaProperties(List.of(
                new EtapaProperties(0, List.of("email-amigavel"), false, false, false),
                new EtapaProperties(5, List.of("email-amigavel", "sms-lembrete"), false, false, false))));

        EtapaWorkflow etapa = resolver.etapaParaDia(5).orElseThrow();

        assertThat(etapa.dia()).isEqualTo(5);
        assertThat(etapa.notificacoes())
                .containsExactly(
                        new NotificacaoEtapa(CanalNotificacao.EMAIL, "cobranca-amigavel"),
                        new NotificacaoEtapa(CanalNotificacao.SMS, "cobranca-lembrete"));
    }

    @Test
    void etapaParaDia_naoConfigurado_retornaEmpty() {
        WorkflowCobrancaResolver resolver = new WorkflowCobrancaResolver(new WorkflowCobrancaProperties(
                List.of(new EtapaProperties(0, List.of("email-amigavel"), false, false, false))));

        assertThat(resolver.etapaParaDia(3)).isEmpty();
    }

    @Test
    void etapaParaDia_flagsPreservadas() {
        WorkflowCobrancaResolver resolver = new WorkflowCobrancaResolver(new WorkflowCobrancaProperties(
                List.of(new EtapaProperties(30, List.of("email-firme"), true, false, false))));

        EtapaWorkflow e = resolver.etapaParaDia(30).orElseThrow();

        assertThat(e.flagContatoManual()).isTrue();
        assertThat(e.escalonarBackoffice()).isFalse();
        assertThat(e.marcarInadimplente()).isFalse();
    }

    @Test
    void parseNotificacao_emailAmigavel() {
        NotificacaoEtapa n = WorkflowCobrancaResolver.parseNotificacao("email-amigavel");

        assertThat(n.canal()).isEqualTo(CanalNotificacao.EMAIL);
        assertThat(n.template()).isEqualTo("cobranca-amigavel");
    }

    @Test
    void parseNotificacao_smsLembrete() {
        NotificacaoEtapa n = WorkflowCobrancaResolver.parseNotificacao("sms-lembrete");

        assertThat(n.canal()).isEqualTo(CanalNotificacao.SMS);
        assertThat(n.template()).isEqualTo("cobranca-lembrete");
    }

    @Test
    void parseNotificacao_canalDesconhecido_rejeita() {
        assertThatThrownBy(() -> WorkflowCobrancaResolver.parseNotificacao("push-novidade"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canal desconhecido");
    }

    @Test
    void parseNotificacao_semHifen_rejeita() {
        assertThatThrownBy(() -> WorkflowCobrancaResolver.parseNotificacao("emailamigavel"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("malformado");
    }
}
