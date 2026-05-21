package com.dynamis.sep_api.contratos.infrastructure.template;

import com.dynamis.sep_api.contratos.application.port.out.TemplateContratoException;
import com.dynamis.sep_api.contratos.application.port.out.dto.TemplateContratoRequest;
import com.dynamis.sep_api.contratos.application.port.out.dto.TemplateContratoResponse;
import com.dynamis.sep_api.contratos.application.service.ClausulaContratoParser;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.domain.vo.TipoContratoSemTemplateException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThymeleafTemplateContratoEngineTest {

    private final ClausulaContratoParser parser = new ClausulaContratoParser();
    private final ThymeleafTemplateContratoEngine engine =
            new ThymeleafTemplateContratoEngine(parser, "templates/", ".txt");

    @Test
    void renderizar_mutuoComVariaveis() {
        UUID propostaId = UUID.randomUUID();
        String clausulas = "CLAUSULA 1 - OBJETO\nTexto.\nCLAUSULA 2 - FORO\nSao Paulo.\n";
        TemplateContratoRequest req = new TemplateContratoRequest(TipoContrato.MUTUO, variaveis(propostaId, clausulas));

        TemplateContratoResponse resp = engine.renderizar(req);

        assertThat(resp.conteudoTexto()).contains("CONTRATO DE MUTUO");
        assertThat(resp.conteudoTexto()).contains(propostaId.toString());
        assertThat(resp.conteudoTexto()).contains("CAPITAL_GIRO");
        assertThat(resp.conteudoTexto()).contains("R$ 10.000,00");
        assertThat(resp.clausulas()).hasSize(2);
        assertThat(resp.clausulas().get(0).titulo()).isEqualTo("OBJETO");
    }

    @Test
    void renderizar_ccbContemAlertaPreparatorio() {
        TemplateContratoRequest req = new TemplateContratoRequest(
                TipoContrato.CCB, variaveis(UUID.randomUUID(), "CLAUSULA 1 - OBJETO\nTexto.\n"));

        TemplateContratoResponse resp = engine.renderizar(req);

        assertThat(resp.conteudoTexto()).contains("CEDULA DE CREDITO BANCARIO");
        assertThat(resp.conteudoTexto()).contains("Sprint 11");
    }

    @Test
    void renderizar_templateInexistente_lancaTemplateException() {
        ThymeleafTemplateContratoEngine engineQuebrado =
                new ThymeleafTemplateContratoEngine(parser, "no-existe/", ".txt");
        TemplateContratoRequest req = new TemplateContratoRequest(
                TipoContrato.MUTUO, variaveis(UUID.randomUUID(), "CLAUSULA 1 - OBJETO\nTexto.\n"));

        assertThatThrownBy(() -> engineQuebrado.renderizar(req)).isInstanceOf(TemplateContratoException.class);
    }

    @Test
    void renderizar_clausulasPadraoSemMarcadores_falha() {
        TemplateContratoRequest req =
                new TemplateContratoRequest(TipoContrato.MUTUO, variaveis(UUID.randomUUID(), "Nenhum marcador aqui."));

        assertThatThrownBy(() -> engine.renderizar(req))
                .isInstanceOf(TemplateContratoException.class)
                .hasMessageContaining("clausulas");
    }

    @Test
    void renderizar_tipoOutros_lancaTipoSemTemplate() {
        TemplateContratoRequest req = new TemplateContratoRequest(
                TipoContrato.OUTROS, variaveis(UUID.randomUUID(), "CLAUSULA 1 - OBJETO\nTexto.\n"));

        assertThatThrownBy(() -> engine.renderizar(req)).isInstanceOf(TipoContratoSemTemplateException.class);
    }

    @Test
    void request_recusaVariaveisFaltando() {
        Map<String, Object> incompleto = new HashMap<>();
        incompleto.put("propostaId", UUID.randomUUID().toString());
        // demais ausentes

        assertThatThrownBy(() -> new TemplateContratoRequest(TipoContrato.MUTUO, incompleto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ausentes");
    }

    @Test
    void request_recusaVariavelEmBranco() {
        Map<String, Object> mapa = new HashMap<>(variaveis(UUID.randomUUID(), "CLAUSULA 1 - OBJETO\nTexto.\n"));
        mapa.put("propostaId", "  ");

        assertThatThrownBy(() -> new TemplateContratoRequest(TipoContrato.MUTUO, mapa))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazias");
    }

    private static Map<String, Object> variaveis(UUID propostaId, String clausulasPadrao) {
        return Map.of(
                "propostaId",
                propostaId.toString(),
                "tomadorId",
                UUID.randomUUID().toString(),
                "tipoOperacao",
                "CAPITAL_GIRO",
                "valorSolicitado",
                "10.000,00",
                "moeda",
                "BRL",
                "prazoMeses",
                12,
                "dataGeracao",
                "20/05/2026",
                "clausulasPadrao",
                clausulasPadrao);
    }
}
