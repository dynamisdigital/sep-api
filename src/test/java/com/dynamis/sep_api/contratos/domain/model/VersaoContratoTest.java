package com.dynamis.sep_api.contratos.domain.model;

import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersaoContratoTest {

    private static final String HASH = "a".repeat(64);

    @Test
    void criar_validaHashLen() {
        Contrato contrato = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);
        assertThatThrownBy(() -> VersaoContrato.criar(contrato, 1, "conteudo", "abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void criar_recusaConteudoVazio() {
        Contrato contrato = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);
        assertThatThrownBy(() -> VersaoContrato.criar(contrato, 1, "  ", HASH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adicionarClausula_ordemPositiva() {
        Contrato contrato = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);
        VersaoContrato versao = contrato.adicionarVersao("conteudo", HASH);
        versao.adicionarClausula(1, "Foro", "Sao Paulo");
        versao.adicionarClausula(2, "Multa", "10%");

        assertThat(versao.getClausulas()).hasSize(2);
        assertThat(versao.getClausulas().get(0).getTitulo()).isEqualTo("Foro");
    }

    @Test
    void aceiteContrato_truncaUserAgentLongo() {
        Contrato contrato = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);
        VersaoContrato versao = contrato.adicionarVersao("conteudo", HASH);
        String userAgentLongo = "U".repeat(700);

        AceiteContrato aceite = AceiteContrato.registrar(versao, contrato.getTomadorId(), "127.0.0.1", userAgentLongo);

        assertThat(aceite.getUserAgentOrigem()).hasSize(500);
    }

    @Test
    void aceiteContrato_aceitaUserAgentNulo() {
        Contrato contrato = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);
        VersaoContrato versao = contrato.adicionarVersao("conteudo", HASH);

        AceiteContrato aceite = AceiteContrato.registrar(versao, contrato.getTomadorId(), null, null);

        assertThat(aceite.getUserAgentOrigem()).isNull();
        assertThat(aceite.getIpOrigem()).isNull();
    }
}
