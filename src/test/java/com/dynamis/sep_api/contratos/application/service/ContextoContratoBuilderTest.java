package com.dynamis.sep_api.contratos.application.service;

import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContextoContratoBuilderTest {

    private final ContextoContratoBuilder builder = new ContextoContratoBuilder(
            new DefaultResourceLoader(), "classpath:templates/contratos/clausulas-padrao.txt");

    @Test
    void construir_montaTodasVariaveisObrigatorias() {
        PropostaCredito proposta = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 24);

        Map<String, Object> ctx = builder.construir(proposta);

        assertThat(ctx)
                .containsKeys(
                        "propostaId",
                        "tomadorId",
                        "tipoOperacao",
                        "valorSolicitado",
                        "moeda",
                        "prazoMeses",
                        "dataGeracao",
                        "clausulasPadrao");
        assertThat(ctx.get("propostaId")).isEqualTo(proposta.getId().toString());
        assertThat(ctx.get("tipoOperacao")).isEqualTo("CAPITAL_GIRO");
        assertThat(ctx.get("prazoMeses")).isEqualTo(24);
        assertThat(ctx.get("moeda")).isEqualTo("BRL");
    }

    @Test
    void construir_valorFormatadoPtBr() {
        PropostaCredito proposta = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("1234.50"), 12);

        Map<String, Object> ctx = builder.construir(proposta);

        // 1.234,50 em locale pt-BR (separador milhar '.', decimal ',')
        assertThat(ctx.get("valorSolicitado").toString()).isEqualTo("1.234,50");
    }

    @Test
    void construir_clausulasPadraoNaoVazia() {
        PropostaCredito proposta = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("100"), 6);

        Map<String, Object> ctx = builder.construir(proposta);

        String clausulas = ctx.get("clausulasPadrao").toString();
        assertThat(clausulas).contains("CLAUSULA 1").contains("CLAUSULA 6");
    }
}
