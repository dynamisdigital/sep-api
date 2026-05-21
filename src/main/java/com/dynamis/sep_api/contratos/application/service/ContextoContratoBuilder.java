package com.dynamis.sep_api.contratos.application.service;

import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Constroi o mapa de variaveis para o {@link com.dynamis.sep_api.contratos.application.port.out
 * .TemplateContratoEngine}.
 *
 * <p>Valores cadastrais do tomador (nome, endereco, documento completo) ainda nao estao
 * disponiveis no modulo {@code credito}; o template usa placeholders tecnicos (UUIDs) e a
 * pendencia esta documentada em {@code CONTRATOS.md} como follow-up para Sprint 11/ajuste
 * juridico. Nao inventamos dados cadastrais.
 *
 * <p>A lista de clausulas padrao e carregada uma unica vez do classpath
 * ({@code classpath:templates/contratos/clausulas-padrao.txt}) e injetada como variavel
 * {@code clausulasPadrao}.
 */
@Component
public class ContextoContratoBuilder {

    private static final Locale LOCALE_PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter FORMATO_DATA_PT_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_PT_BR);

    private final String clausulasPadrao;

    public ContextoContratoBuilder(
            ResourceLoader resourceLoader,
            @Value("${app.contratos.clausulas-padrao-path:classpath:templates/contratos/clausulas-padrao.txt}")
                    String clausulasPath) {
        this.clausulasPadrao = carregar(resourceLoader, clausulasPath);
    }

    public Map<String, Object> construir(PropostaCredito proposta) {
        return Map.of(
                "propostaId", proposta.getId().toString(),
                "tomadorId", proposta.getTomadorId().toString(),
                "tipoOperacao", proposta.getTipoOperacao().name(),
                "valorSolicitado", formatarValor(proposta.getValorSolicitado()),
                "moeda", proposta.getMoeda(),
                "prazoMeses", proposta.getPrazoMeses(),
                "dataGeracao", FORMATO_DATA_PT_BR.format(OffsetDateTime.now()),
                "clausulasPadrao", clausulasPadrao);
    }

    private static String formatarValor(java.math.BigDecimal valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(LOCALE_PT_BR);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor);
    }

    private static String carregar(ResourceLoader resourceLoader, String path) {
        try {
            Resource resource = resourceLoader.getResource(path);
            try (var input = resource.getInputStream()) {
                return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao carregar clausulas padrao em " + path, e);
        }
    }
}
