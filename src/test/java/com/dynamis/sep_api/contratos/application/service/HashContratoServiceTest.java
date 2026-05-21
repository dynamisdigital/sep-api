package com.dynamis.sep_api.contratos.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashContratoServiceTest {

    private final HashContratoService service = new HashContratoService();

    @Test
    void calcular_sha256HexLowercase64Chars() {
        // SHA-256 de "abc" = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        String hash = service.calcular("abc");

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[a-f0-9]{64}$");
    }

    @Test
    void calcular_textosDiferentesGeramHashesDiferentes() {
        assertThat(service.calcular("texto A")).isNotEqualTo(service.calcular("texto B"));
    }

    @Test
    void calcular_mesmoTextoGeraMesmoHash() {
        String t = "Contrato de mutuo entre partes XYZ.";
        assertThat(service.calcular(t)).isEqualTo(service.calcular(t));
    }

    @Test
    void calcular_recusaNull() {
        assertThatThrownBy(() -> service.calcular((String) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calcular((byte[]) null)).isInstanceOf(IllegalArgumentException.class);
    }
}
