package com.dynamis.sep_api.pix.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Funcoes de minimizacao da chave Pix destino (Sprint 20): a chave em claro nunca eh persistida. O
 * {@link #hashHex(String)} permite verificar consistencia idempotente sem guardar a chave; a {@link
 * #mascarar(String)} produz um texto seguro para resposta/auditoria.
 */
public final class ChavePixSeguranca {

    private ChavePixSeguranca() {}

    /** SHA-256 hex (64 chars) da chave normalizada (trim). */
    public static String hashHex(String chave) {
        Objects.requireNonNull(chave, "chave obrigatoria");
        String normalizada = chave.strip();
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(normalizada.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 nao disponivel", ex);
        }
    }

    /**
     * Mascara a chave preservando os 2 primeiros e 2 ultimos caracteres; o miolo vira asteriscos.
     * Chaves de ate 4 caracteres viram apenas asteriscos. Resultado limitado a 80 caracteres.
     */
    public static String mascarar(String chave) {
        String normalizada = chave == null ? "" : chave.strip();
        int len = normalizada.length();
        if (len == 0) {
            return "";
        }
        if (len <= 4) {
            return "*".repeat(len);
        }
        int visiveis = 2;
        String mascarada = normalizada.substring(0, visiveis)
                + "*".repeat(len - visiveis * 2)
                + normalizada.substring(len - visiveis);
        return mascarada.length() > 80 ? mascarada.substring(0, 80) : mascarada;
    }
}
