package com.dynamis.sep_api.contratos.domain.model;

import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContratoTest {

    private static final String HASH_FAKE = "0".repeat(64);

    @Test
    void criar_iniciaEmGerado() {
        Contrato contrato = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);

        assertThat(contrato.getStatus()).isEqualTo(StatusFormalizacao.GERADO);
        assertThat(contrato.getVersoes()).isEmpty();
        assertThat(contrato.versaoVigente()).isEmpty();
    }

    @Test
    void adicionarVersao_primeira_transicionaParaAguardandoAceite() {
        Contrato contrato = novoContrato();

        VersaoContrato v1 = contrato.adicionarVersao("conteudo v1", HASH_FAKE);

        assertThat(v1.getNumero()).isEqualTo(1);
        assertThat(contrato.getStatus()).isEqualTo(StatusFormalizacao.AGUARDANDO_ACEITE);
        assertThat(contrato.versaoVigente()).contains(v1);
    }

    @Test
    void adicionarVersao_regeneracaoIncrementaNumero() {
        Contrato contrato = novoContrato();
        contrato.adicionarVersao("v1", HASH_FAKE);

        VersaoContrato v2 = contrato.adicionarVersao("v2", HASH_FAKE);

        assertThat(v2.getNumero()).isEqualTo(2);
        assertThat(contrato.getStatus()).isEqualTo(StatusFormalizacao.AGUARDANDO_ACEITE);
    }

    @Test
    void adicionarVersao_emAceito_rejeita() {
        Contrato contrato = novoContrato();
        contrato.adicionarVersao("v1", HASH_FAKE);
        contrato.marcarAceito();

        assertThatThrownBy(() -> contrato.adicionarVersao("v2", HASH_FAKE))
                .isInstanceOf(ContratoEstadoInvalidoException.class);
    }

    @Test
    void marcarAceito_apenasEmAguardandoAceite() {
        Contrato contrato = novoContrato();

        assertThatThrownBy(contrato::marcarAceito).isInstanceOf(ContratoEstadoInvalidoException.class);

        contrato.adicionarVersao("v1", HASH_FAKE);
        contrato.marcarAceito();
        assertThat(contrato.getStatus()).isEqualTo(StatusFormalizacao.ACEITO);

        assertThatThrownBy(contrato::marcarAceito).isInstanceOf(ContratoEstadoInvalidoException.class);
    }

    @Test
    void cancelar_emGeradoEAguardando_ok() {
        Contrato contrato = novoContrato();
        contrato.cancelar();
        assertThat(contrato.getStatus()).isEqualTo(StatusFormalizacao.CANCELADO);

        Contrato c2 = novoContrato();
        c2.adicionarVersao("v1", HASH_FAKE);
        c2.cancelar();
        assertThat(c2.getStatus()).isEqualTo(StatusFormalizacao.CANCELADO);
    }

    @Test
    void cancelar_emAceito_rejeita() {
        Contrato contrato = novoContrato();
        contrato.adicionarVersao("v1", HASH_FAKE);
        contrato.marcarAceito();

        assertThatThrownBy(contrato::cancelar).isInstanceOf(ContratoEstadoInvalidoException.class);
    }

    @Test
    void status_helpers() {
        assertThat(StatusFormalizacao.GERADO.permiteNovaVersao()).isTrue();
        assertThat(StatusFormalizacao.AGUARDANDO_ACEITE.permiteNovaVersao()).isTrue();
        assertThat(StatusFormalizacao.ACEITO.permiteNovaVersao()).isFalse();
        assertThat(StatusFormalizacao.AGUARDANDO_ACEITE.permiteAceite()).isTrue();
        assertThat(StatusFormalizacao.GERADO.permiteAceite()).isFalse();
        assertThat(StatusFormalizacao.AGUARDANDO_ACEITE.permiteCancelamento()).isTrue();
        assertThat(StatusFormalizacao.ACEITO.permiteCancelamento()).isFalse();
        assertThat(StatusFormalizacao.CANCELADO.isFinal()).isTrue();
        assertThat(StatusFormalizacao.ASSINADO.isFinal()).isTrue();
        assertThat(StatusFormalizacao.GERADO.isFinal()).isFalse();
    }

    private Contrato novoContrato() {
        return Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);
    }
}
