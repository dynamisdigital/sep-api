package com.dynamis.sep_api.pix.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ChavePixSegurancaTest {

    @Test
    void hashHex_eDeterministicoE64HexChars() {
        String h1 = ChavePixSeguranca.hashHex("usuario@empresa.com");
        String h2 = ChavePixSeguranca.hashHex("usuario@empresa.com");

        assertThat(h1).isEqualTo(h2).matches("^[a-f0-9]{64}$");
    }

    @Test
    void hashHex_normalizaTrim() {
        assertThat(ChavePixSeguranca.hashHex("  chave  ")).isEqualTo(ChavePixSeguranca.hashHex("chave"));
    }

    @Test
    void hashHex_chaveNula_lancaNpe() {
        assertThatNullPointerException().isThrownBy(() -> ChavePixSeguranca.hashHex(null));
    }

    @Test
    void mascarar_preservaPrimeirosEUltimosDois() {
        assertThat(ChavePixSeguranca.mascarar("usuario@empresa.com"))
                .startsWith("us")
                .endsWith("om")
                .contains("*");
    }

    @Test
    void mascarar_chaveCurta_viraAsteriscos() {
        assertThat(ChavePixSeguranca.mascarar("abcd")).isEqualTo("****");
    }

    @Test
    void mascarar_nulaOuVazia_retornaVazio() {
        assertThat(ChavePixSeguranca.mascarar(null)).isEmpty();
        assertThat(ChavePixSeguranca.mascarar("   ")).isEmpty();
    }
}
