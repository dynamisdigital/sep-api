package com.dynamis.sep_api.credito.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFinancePayloadSanitizerTest {

    private final OpenFinancePayloadSanitizer sanitizer = new OpenFinancePayloadSanitizer(new ObjectMapper());

    @Test
    void removeCamposSensiveisTopLevel() {
        String out = sanitizer.sanitize("{\"saldo\":100,\"cpf\":\"52998224725\",\"account_number\":\"123\"}");
        assertThat(out).contains("saldo").doesNotContain("cpf").doesNotContain("account_number");
    }

    @Test
    void removeCamposSensiveisAninhados() {
        String out = sanitizer.sanitize("{\"meta\":{\"holder_document\":\"x\",\"agency_number\":\"y\"},\"saldo\":1}");
        assertThat(out).contains("saldo").doesNotContain("holder_document").doesNotContain("agency_number");
    }

    @Test
    void removeTransacoesBrutas() {
        String out = sanitizer.sanitize("{\"transactions\":[{\"v\":1},{\"v\":2}],\"saldo\":1}");
        assertThat(out).doesNotContain("transactions").contains("saldo");
    }

    @Test
    void payloadInvalidoDevolveOriginal() {
        String original = "not-json";
        assertThat(sanitizer.sanitize(original)).isEqualTo(original);
    }

    @Test
    void nullEBlankPreservados() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("")).isEmpty();
    }
}
