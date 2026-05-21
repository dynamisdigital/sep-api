package com.dynamis.sep_api.contratos.domain.vo;

import java.util.regex.Pattern;

/**
 * Validador de hash SHA-256 hex lowercase. Usado pelas entidades do modulo {@code contratos} que
 * carregam integridade criptografica: {@code VersaoContrato.hashSha256}, {@code
 * EnvelopeAssinatura.hashPdfEnviado} e {@code DocumentoAssinado.hashSha256}.
 *
 * <p>Formato exigido: 64 caracteres em {@code [a-f0-9]} (lowercase). Casing fixado para evitar
 * divergencia entre persistencia e comparacao.
 */
public final class HashValidator {

    private static final Pattern SHA256_HEX = Pattern.compile("^[a-f0-9]{64}$");

    private HashValidator() {}

    public static boolean isValid(String hash) {
        return hash != null && SHA256_HEX.matcher(hash).matches();
    }

    public static String requireValid(String hash, String campo) {
        if (!isValid(hash)) {
            throw new IllegalArgumentException(campo + " invalido: deve ser SHA-256 hex lowercase com 64 caracteres");
        }
        return hash;
    }
}
