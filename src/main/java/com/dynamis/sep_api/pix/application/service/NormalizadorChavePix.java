package com.dynamis.sep_api.pix.application.service;

import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import com.dynamis.sep_api.shared.exception.ValidacaoException;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Normalizacao deterministica da chave Pix por tipo (Sprint 31, Gate 31.0): o mesmo valor
 * normalizado alimenta hash, mascara e provider. Valores invalidos falham com {@link
 * ValidacaoException} <strong>sem ecoar o valor bruto</strong> na mensagem.
 *
 * <ul>
 *   <li>CPF/CNPJ: remove pontuacao ({@code . - /} e espacos); exige 11/14 digitos.
 *   <li>TELEFONE: remove formatacao ({@code ( ) - .} e espacos); canonico E.164 Brasil
 *       ({@code +55DDDNUMERO}); sem DDI assume {@code +55}.
 *   <li>EMAIL: trim + lowercase; formato basico; limite DICT de 77 caracteres.
 *   <li>EVP: UUID canonico lowercase.
 * </ul>
 */
public final class NormalizadorChavePix {

    static final String CODIGO_TIPO_OBRIGATORIO = "PIX-400-CHAVE-TIPO";
    static final String CODIGO_CHAVE_INVALIDA = "PIX-400-CHAVE";

    private static final Pattern PONTUACAO_DOCUMENTO = Pattern.compile("[.\\-/\\s]");
    private static final Pattern FORMATACAO_TELEFONE = Pattern.compile("[().\\-\\s]");
    private static final Pattern SO_DIGITOS = Pattern.compile("\\d+");
    private static final Pattern EMAIL_BASICO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int LIMITE_EMAIL_DICT = 77;

    private NormalizadorChavePix() {}

    /** Normaliza {@code valor} conforme o {@code tipo}; retorna o valor canonico a hashear/enviar. */
    public static String normalizar(TipoChavePix tipo, String valor) {
        if (tipo == null) {
            throw new ValidacaoException(CODIGO_TIPO_OBRIGATORIO, "tipo da chave Pix obrigatorio.");
        }
        if (valor == null || valor.isBlank()) {
            throw new ValidacaoException(CODIGO_CHAVE_INVALIDA, "valor da chave Pix obrigatorio.");
        }
        String base = valor.strip();
        return switch (tipo) {
            case CPF -> normalizarDocumento(base, 11, tipo);
            case CNPJ -> normalizarDocumento(base, 14, tipo);
            case TELEFONE -> normalizarTelefone(base);
            case EMAIL -> normalizarEmail(base);
            case EVP -> normalizarEvp(base);
        };
    }

    private static String normalizarDocumento(String valor, int digitos, TipoChavePix tipo) {
        String semPontuacao = PONTUACAO_DOCUMENTO.matcher(valor).replaceAll("");
        if (semPontuacao.length() != digitos
                || !SO_DIGITOS.matcher(semPontuacao).matches()) {
            throw chaveInvalida(tipo);
        }
        return semPontuacao;
    }

    private static String normalizarTelefone(String valor) {
        String semFormatacao = FORMATACAO_TELEFONE.matcher(valor).replaceAll("");
        String digitos = semFormatacao.startsWith("+55")
                ? semFormatacao.substring(3)
                : semFormatacao.startsWith("+") ? null : semFormatacao;
        if (digitos == null
                || !SO_DIGITOS.matcher(digitos).matches()
                || digitos.length() < 10
                || digitos.length() > 11) {
            throw chaveInvalida(TipoChavePix.TELEFONE);
        }
        return "+55" + digitos;
    }

    private static String normalizarEmail(String valor) {
        String normalizado = valor.toLowerCase();
        if (normalizado.length() > LIMITE_EMAIL_DICT
                || !EMAIL_BASICO.matcher(normalizado).matches()) {
            throw chaveInvalida(TipoChavePix.EMAIL);
        }
        return normalizado;
    }

    private static String normalizarEvp(String valor) {
        String normalizado = valor.toLowerCase();
        if (normalizado.length() != 36) {
            throw chaveInvalida(TipoChavePix.EVP);
        }
        try {
            UUID uuid = UUID.fromString(normalizado);
            return uuid.toString();
        } catch (IllegalArgumentException ex) {
            throw chaveInvalida(TipoChavePix.EVP);
        }
    }

    private static ValidacaoException chaveInvalida(TipoChavePix tipo) {
        return new ValidacaoException(CODIGO_CHAVE_INVALIDA, "chave Pix invalida para o tipo " + tipo + ".");
    }
}
