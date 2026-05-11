package com.dynamis.sep_api.identity.domain.vo;

import com.dynamis.sep_api.identity.application.exception.SenhaComprometidaException;
import com.dynamis.sep_api.identity.application.exception.SenhaFracaException;
import com.dynamis.sep_api.identity.application.port.out.PasswordBreachChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordPolicyTest {

    private PasswordBreachChecker breachChecker;
    private PasswordPolicy policy;

    @BeforeEach
    void setup() {
        breachChecker = mock(PasswordBreachChecker.class);
        when(breachChecker.foiVazada(any())).thenReturn(false);
        policy = new PasswordPolicy(breachChecker);
    }

    private static String any() {
        return org.mockito.ArgumentMatchers.any();
    }

    @Test
    void aceitaSenhaCom12CharsOuMais() {
        assertThatNoException().isThrownBy(() -> policy.validar("senha-com-12+"));
    }

    @Test
    void rejeitaSenhaCom11OuMenos() {
        assertThatThrownBy(() -> policy.validar("curtaXX")).isInstanceOf(SenhaFracaException.class);
        assertThatThrownBy(() -> policy.validar("12345678901")).isInstanceOf(SenhaFracaException.class);
    }

    @Test
    void aceitaPassphraseCom4OuMaisPalavrasGrandes() {
        assertThatNoException().isThrownBy(() -> policy.validar("uma boa frase aqui"));
    }

    @Test
    void rejeitaPassphraseComMenosDe4Palavras() {
        assertThatThrownBy(() -> policy.validar("tres aa bb")).isInstanceOf(SenhaFracaException.class);
    }

    @Test
    void rejeitaPassphraseComPalavraCurta() {
        assertThatThrownBy(() -> policy.validar("ok ab cd ef")).isInstanceOf(SenhaFracaException.class);
    }

    @Test
    void rejeitaSenhaVazia() {
        assertThatThrownBy(() -> policy.validar("")).isInstanceOf(SenhaFracaException.class);
        assertThatThrownBy(() -> policy.validar(null)).isInstanceOf(SenhaFracaException.class);
    }

    @Test
    void rejeitaSenhaVazada() {
        when(breachChecker.foiVazada("senha-vazada-1234")).thenReturn(true);

        assertThatThrownBy(() -> policy.validar("senha-vazada-1234")).isInstanceOf(SenhaComprometidaException.class);
    }
}
