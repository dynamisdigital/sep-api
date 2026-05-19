package com.dynamis.sep_api.credito.domain.model;

import com.dynamis.sep_api.credito.domain.exception.ConsentimentoInvalidoException;
import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsentimentoOpenFinanceTest {

    @Test
    void iniciarCriaPendente() {
        UUID prop = UUID.randomUUID();
        UUID tom = UUID.randomUUID();
        OffsetDateTime exp = OffsetDateTime.now().plusDays(30);
        ConsentimentoOpenFinance c =
                ConsentimentoOpenFinance.iniciar(prop, tom, "https://celcoin/auth/abc", "ext-1", exp);

        assertThat(c.getId()).isNotNull();
        assertThat(c.getPropostaId()).isEqualTo(prop);
        assertThat(c.getTomadorId()).isEqualTo(tom);
        assertThat(c.getStatus()).isEqualTo(StatusConsentimento.PENDENTE);
        assertThat(c.getUrlAutorizacao()).isEqualTo("https://celcoin/auth/abc");
        assertThat(c.getIdExternoCelcoin()).isEqualTo("ext-1");
        assertThat(c.getDataInicio()).isNotNull();
        assertThat(c.getDataExpiracao()).isEqualTo(exp);
        assertThat(c.getDataAutorizacao()).isNull();
    }

    @Test
    void autorizarTransicaoValida() {
        ConsentimentoOpenFinance c = consentimento();
        c.autorizar();
        assertThat(c.getStatus()).isEqualTo(StatusConsentimento.AUTORIZADO);
        assertThat(c.getDataAutorizacao()).isNotNull();
    }

    @Test
    void autorizarSegundaVezFalha() {
        ConsentimentoOpenFinance c = consentimento();
        c.autorizar();
        assertThatThrownBy(c::autorizar).isInstanceOf(ConsentimentoInvalidoException.class);
    }

    @Test
    void negarTransicaoValida() {
        ConsentimentoOpenFinance c = consentimento();
        c.negar();
        assertThat(c.getStatus()).isEqualTo(StatusConsentimento.NEGADO);
    }

    @Test
    void negarAposAutorizarFalha() {
        ConsentimentoOpenFinance c = consentimento();
        c.autorizar();
        assertThatThrownBy(c::negar).isInstanceOf(ConsentimentoInvalidoException.class);
    }

    @Test
    void expirarTransicaoValida() {
        ConsentimentoOpenFinance c = consentimento();
        c.expirar();
        assertThat(c.getStatus()).isEqualTo(StatusConsentimento.EXPIRADO);
    }

    @Test
    void expirarAposAutorizarFalha() {
        ConsentimentoOpenFinance c = consentimento();
        c.autorizar();
        assertThatThrownBy(c::expirar).isInstanceOf(ConsentimentoInvalidoException.class);
    }

    @Test
    void statusFinalsBloqueiamMutacoes() {
        assertThat(StatusConsentimento.AUTORIZADO.isFinal()).isTrue();
        assertThat(StatusConsentimento.NEGADO.isFinal()).isTrue();
        assertThat(StatusConsentimento.EXPIRADO.isFinal()).isTrue();
        assertThat(StatusConsentimento.PENDENTE.isFinal()).isFalse();
        assertThat(StatusConsentimento.AUTORIZADO.permiteConsulta()).isTrue();
        assertThat(StatusConsentimento.PENDENTE.permiteConsulta()).isFalse();
    }

    private static ConsentimentoOpenFinance consentimento() {
        return ConsentimentoOpenFinance.iniciar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "https://celcoin/auth/abc",
                "ext-" + UUID.randomUUID(),
                OffsetDateTime.now().plusDays(30));
    }
}
