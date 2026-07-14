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
 *   <li>CPF/CNPJ: remove pontuacao ({@code . - /} e espacos); exige 11/14 digitos com digitos
 *       verificadores validos (mod-11) e rejeita sequencias de digito repetido.
 *   <li>TELEFONE: remove formatacao ({@code ( ) - .} e espacos); canonico E.164 Brasil
 *       ({@code +55DDDNUMERO}, DDD com ambos os digitos 1-9); sem DDI assume {@code +55}.
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
    private static final Pattern EMAIL_BASICO = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)+$");
    private static final Pattern DDD_VALIDO = Pattern.compile("[1-9][1-9]");
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
                || !SO_DIGITOS.matcher(semPontuacao).matches()
                || sequenciaRepetida(semPontuacao)
                || !digitosVerificadoresValidos(semPontuacao, tipo)) {
            throw chaveInvalida(tipo);
        }
        return semPontuacao;
    }

    private static boolean sequenciaRepetida(String documento) {
        return documento.chars().distinct().count() == 1;
    }

    private static boolean digitosVerificadoresValidos(String documento, TipoChavePix tipo) {
        return tipo == TipoChavePix.CPF ? dvCpfValido(documento) : dvCnpjValido(documento);
    }

    /** DV mod-11 do CPF: pesos 10..2 (1o digito) e 11..2 (2o digito). */
    private static boolean dvCpfValido(String cpf) {
        return digito(cpf, 9, 10) == cpf.charAt(9) - '0' && digito(cpf, 10, 11) == cpf.charAt(10) - '0';
    }

    private static int digito(String documento, int tamanho, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (documento.charAt(i) - '0') * (pesoInicial - i);
        }
        return dvMod11(soma);
    }

    /** DV mod-11 do CNPJ: pesos 5,4,3,2,9..2 (1o digito) e 6,5,4,3,2,9..2 (2o digito). */
    private static boolean dvCnpjValido(String cnpj) {
        int[] pesos1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesos2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return digitoCnpj(cnpj, pesos1) == cnpj.charAt(12) - '0' && digitoCnpj(cnpj, pesos2) == cnpj.charAt(13) - '0';
    }

    private static int digitoCnpj(String cnpj, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (cnpj.charAt(i) - '0') * pesos[i];
        }
        return dvMod11(soma);
    }

    private static int dvMod11(int soma) {
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static String normalizarTelefone(String valor) {
        String semFormatacao = FORMATACAO_TELEFONE.matcher(valor).replaceAll("");
        String digitos = semFormatacao.startsWith("+55")
                ? semFormatacao.substring(3)
                : semFormatacao.startsWith("+") ? null : semFormatacao;
        if (digitos == null
                || !SO_DIGITOS.matcher(digitos).matches()
                || digitos.length() < 10
                || digitos.length() > 11
                || !DDD_VALIDO.matcher(digitos.substring(0, 2)).matches()) {
            throw chaveInvalida(TipoChavePix.TELEFONE);
        }
        return "+55" + digitos;
    }

    private static String normalizarEmail(String valor) {
        String normalizado = valor.toLowerCase();
        if (normalizado.length() > LIMITE_EMAIL_DICT
                || normalizado.contains("..")
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
