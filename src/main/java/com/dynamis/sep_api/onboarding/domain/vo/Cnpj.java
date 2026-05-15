package com.dynamis.sep_api.onboarding.domain.vo;

import java.util.Objects;

/**
 * Value object para CNPJ. Persiste apenas 14 digitos normalizados via {@link #valor()}; exibicao
 * formatada via {@link #formatado()}.
 *
 * <p>Construtor canonico valida tamanho, sequencias repetidas e os 2 digitos verificadores. Lanca
 * {@link IllegalArgumentException} para CNPJ invalido — use cases mapeiam para
 * {@code ValidacaoException}.
 */
public record Cnpj(String valor) {

    private static final int[] PESOS_DV1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_DV2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    public Cnpj {
        Objects.requireNonNull(valor, "cnpj nao pode ser nulo");
        valor = valor.replaceAll("\\D", "");
        if (valor.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve ter 14 digitos");
        }
        if (isSequenciaRepetida(valor)) {
            throw new IllegalArgumentException("CNPJ nao pode ser sequencia repetida");
        }
        if (!digitosVerificadoresValidos(valor)) {
            throw new IllegalArgumentException("CNPJ com digitos verificadores invalidos");
        }
    }

    /** Devolve formato {@code 00.000.000/0000-00}. */
    public String formatado() {
        return valor.substring(0, 2) + "."
                + valor.substring(2, 5) + "."
                + valor.substring(5, 8) + "/"
                + valor.substring(8, 12) + "-"
                + valor.substring(12, 14);
    }

    private static boolean isSequenciaRepetida(String digitos) {
        char primeiro = digitos.charAt(0);
        for (int i = 1; i < digitos.length(); i++) {
            if (digitos.charAt(i) != primeiro) {
                return false;
            }
        }
        return true;
    }

    private static boolean digitosVerificadoresValidos(String digitos) {
        int dv1 = calcularDigito(digitos, PESOS_DV1);
        int dv2 = calcularDigito(digitos, PESOS_DV2);
        return dv1 == Character.getNumericValue(digitos.charAt(12))
                && dv2 == Character.getNumericValue(digitos.charAt(13));
    }

    private static int calcularDigito(String digitos, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += Character.getNumericValue(digitos.charAt(i)) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
