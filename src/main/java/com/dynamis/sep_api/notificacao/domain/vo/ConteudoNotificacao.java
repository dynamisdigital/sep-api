package com.dynamis.sep_api.notificacao.domain.vo;

import java.util.Objects;

/**
 * O que a notificacao diz: titulo e mensagem montados de texto fixo por tipo, mais a referencia
 * opcional (ADR 0021 §7). Nada do evento de origem entra aqui como texto livre.
 */
public record ConteudoNotificacao(String titulo, String mensagem, Referencia referencia) {

    static final int TAMANHO_MAXIMO_TITULO = 120;
    static final int TAMANHO_MAXIMO_MENSAGEM = 500;

    public ConteudoNotificacao {
        validarTexto(titulo, TAMANHO_MAXIMO_TITULO, "titulo");
        validarTexto(mensagem, TAMANHO_MAXIMO_MENSAGEM, "mensagem");
    }

    private static void validarTexto(String valor, int maximo, String campo) {
        Objects.requireNonNull(valor, campo + " obrigatorio");
        if (valor.isBlank() || valor.length() > maximo) {
            throw new IllegalArgumentException(campo + " deve ter entre 1 e " + maximo + " caracteres");
        }
    }
}
