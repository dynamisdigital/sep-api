package com.dynamis.sep_api.notificacao.application.port.out.dto;

import java.util.Objects;

/**
 * Mensagem a enviar por e-mail. O endereco so existe em memoria durante o envio: nao e persistido
 * (ADR 0021 §2).
 *
 * <p>{@link #toString()} omite endereco, assunto e corpo, para que um log descuidado do objeto nao
 * vaze dado pessoal.
 */
public record EmailNotificacao(String destinatario, String assunto, String corpo) {

    public EmailNotificacao {
        exigirTexto(destinatario, "destinatario");
        exigirTexto(assunto, "assunto");
        exigirTexto(corpo, "corpo");
    }

    private static void exigirTexto(String valor, String campo) {
        Objects.requireNonNull(valor, campo + " obrigatorio");
        if (valor.isBlank()) {
            throw new IllegalArgumentException(campo + " obrigatorio");
        }
    }

    @Override
    public String toString() {
        return "EmailNotificacao[conteudo omitido]";
    }
}
