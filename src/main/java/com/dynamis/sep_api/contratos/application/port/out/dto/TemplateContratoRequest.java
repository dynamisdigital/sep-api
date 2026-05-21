package com.dynamis.sep_api.contratos.application.port.out.dto;

import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Solicitacao de renderizacao de template de contrato (Sprint 10 Task 10.2). Encapsula o tipo do
 * contrato (que define template a usar) e o mapa de variaveis. A port nao deve vazar classes do
 * engine (Thymeleaf, etc).
 *
 * <p>O construtor valida o conjunto minimo de variaveis ({@link #VARIAVEIS_OBRIGATORIAS}) e
 * recusa valores nulos ou em branco — contrato e documento legal e o template renderiza tudo o
 * que for passado, entao falhar cedo aqui evita gerar contrato com campos criticos vazios.
 */
public record TemplateContratoRequest(TipoContrato tipo, Map<String, Object> variaveis) {

    public static final Set<String> VARIAVEIS_OBRIGATORIAS = Set.of(
            "propostaId",
            "tomadorId",
            "tipoOperacao",
            "valorSolicitado",
            "moeda",
            "prazoMeses",
            "dataGeracao",
            "clausulasPadrao");

    public TemplateContratoRequest {
        Objects.requireNonNull(tipo, "tipo obrigatorio");
        Objects.requireNonNull(variaveis, "variaveis obrigatorias");
        validar(variaveis);
        variaveis = Map.copyOf(variaveis);
    }

    private static void validar(Map<String, Object> variaveis) {
        Set<String> faltando = new TreeSet<>();
        Set<String> emBranco = new TreeSet<>();
        for (String chave : VARIAVEIS_OBRIGATORIAS) {
            if (!variaveis.containsKey(chave)) {
                faltando.add(chave);
                continue;
            }
            Object valor = variaveis.get(chave);
            if (valor == null
                    || (valor instanceof CharSequence cs && cs.toString().isBlank())) {
                emBranco.add(chave);
            }
        }
        if (!faltando.isEmpty() || !emBranco.isEmpty()) {
            StringBuilder msg = new StringBuilder("TemplateContratoRequest invalido:");
            if (!faltando.isEmpty()) {
                msg.append(" variaveis ausentes=").append(faltando);
            }
            if (!emBranco.isEmpty()) {
                msg.append(" variaveis vazias=").append(emBranco);
            }
            throw new IllegalArgumentException(msg.toString());
        }
    }
}
