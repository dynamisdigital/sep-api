package com.dynamis.sep_api.cobranca.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowCobrancaTest {

    @Test
    void criar_compila_CSVAPartirDaLista() {
        WorkflowCobranca w =
                WorkflowCobranca.criar("default", 5, List.of("email-amigavel", "sms-lembrete"), false, false, false);

        assertThat(w.getNome()).isEqualTo("default");
        assertThat(w.getDiaAtraso()).isEqualTo(5);
        assertThat(w.getNotificacoes()).containsExactly("email-amigavel", "sms-lembrete");
        assertThat(w.isAtivo()).isTrue();
        assertThat(w.isFlagContatoManual()).isFalse();
        assertThat(w.isEscalonarBackoffice()).isFalse();
        assertThat(w.isMarcarInadimplente()).isFalse();
    }

    @Test
    void criar_listaVazia_retornaNotificacoesVazias() {
        WorkflowCobranca w = WorkflowCobranca.criar("default", 90, List.of(), false, false, true);

        assertThat(w.getNotificacoes()).isEmpty();
        assertThat(w.isMarcarInadimplente()).isTrue();
    }

    @Test
    void criar_nomeVazio_rejeita() {
        assertThatThrownBy(() -> WorkflowCobranca.criar("  ", 0, List.of("x"), false, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nome");
    }

    @Test
    void criar_diaNegativo_rejeita() {
        assertThatThrownBy(() -> WorkflowCobranca.criar("default", -1, List.of("x"), false, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diaAtraso");
    }

    @Test
    void desativar_marcaInativo() {
        WorkflowCobranca w = WorkflowCobranca.criar("default", 30, List.of("x"), true, false, false);

        w.desativar();

        assertThat(w.isAtivo()).isFalse();
    }

    @Test
    void criar_templateComVirgula_rejeita() {
        assertThatThrownBy(() -> WorkflowCobranca.criar("default", 0, List.of("email, urgente"), false, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template invalido");
    }

    @Test
    void criar_templateComEspaco_rejeita() {
        assertThatThrownBy(() -> WorkflowCobranca.criar("default", 0, List.of("email amigavel"), false, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template invalido");
    }

    @Test
    void criar_templateNull_rejeita() {
        java.util.List<String> comNull = new java.util.ArrayList<>();
        comNull.add("email-amigavel");
        comNull.add(null);
        assertThatThrownBy(() -> WorkflowCobranca.criar("default", 0, comNull, false, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template invalido");
    }
}
