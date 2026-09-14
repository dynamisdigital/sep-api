package com.dynamis.sep_api.notificacao.application.port.out.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailNotificacaoTest {

    @Test
    void toString_naoExpoeEnderecoAssuntoNemCorpo() {
        EmailNotificacao email =
                new EmailNotificacao("cliente@sep.test", "Conta bloqueada", "CPF 12345678900; token secreto");

        assertThat(email.toString())
                .doesNotContain("cliente@sep.test", "Conta bloqueada", "12345678900", "token secreto");
    }

    @Test
    void exigeDestinatarioAssuntoECorpo() {
        assertThatThrownBy(() -> new EmailNotificacao(" ", "Assunto", "Corpo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailNotificacao("a@sep.test", "", "Corpo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailNotificacao("a@sep.test", "Assunto", null))
                .isInstanceOf(NullPointerException.class);
    }
}
