package com.dynamis.sep_api.onboarding.domain.vo;

import java.util.Objects;

/**
 * Value object para CPF. Persiste apenas 11 digitos normalizados via {@link #valor()}; exibicao
 * formatada via {@link #formatado()}.
 *
 * <p>Construtor canonico valida tamanho, sequencias repetidas e os 2 digitos verificadores. Lanca
 * {@link IllegalArgumentException} para CPF invalido — use cases mapeiam para
 * {@code ValidacaoException}.
 */
public record Cpf(String valor) {

    public Cpf {
        Objects.requireNonNull(valor, "cpf nao pode ser nulo");
        valor = valor.replaceAll("\\D", "");
        if (valor.length() != 11) {
            throw new IllegalArgumentException("CPF deve ter 11 digitos");
        }
        if (isSequenciaRepetida(valor)) {
            throw new IllegalArgumentException("CPF nao pode ser sequencia repetida");
        }
        if (!digitosVerificadoresValidos(valor)) {
            throw new IllegalArgumentException("CPF com digitos verificadores invalidos");
        }
    }

    /** Devolve formato {@code 000.000.000-00}. */
    public String formatado() {
        return valor.substring(0, 3) + "." + valor.substring(3, 6) + "." + valor.substring(6, 9) + "-"
                + valor.substring(9, 11);
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
        int dv1 = calcularDigito(digitos, 9, 10);
        int dv2 = calcularDigito(digitos, 10, 11);
        return dv1 == Character.getNumericValue(digitos.charAt(9))
                && dv2 == Character.getNumericValue(digitos.charAt(10));
    }

    private static int calcularDigito(String digitos, int ate, int pesoInicial) {
        int soma = 0;
        int peso = pesoInicial;
        for (int i = 0; i < ate; i++) {
            soma += Character.getNumericValue(digitos.charAt(i)) * peso;
            peso--;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
