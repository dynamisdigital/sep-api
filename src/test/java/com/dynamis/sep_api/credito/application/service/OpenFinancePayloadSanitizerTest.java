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
    void removeCamposSensiveisAninhadosDentroDeArray() {
        // Cobertura cross caveman #5: provider pode mudar payload pra arrays profundos.
        String out = sanitizer.sanitize(
                "{\"data\":[{\"accounts\":[{\"account_number\":\"x\",\"saldo\":100},{\"cpf\":\"y\"}]}]}");
        assertThat(out).doesNotContain("account_number").doesNotContain("cpf").contains("saldo");
    }

    @Test
    void payloadInvalidoFalhaFechadaComPlaceholder() {
        String original = "not-json-with-pii-cpf-52998224725";
        String out = sanitizer.sanitize(original);
        assertThat(out)
                .doesNotContain("52998224725")
                .doesNotContain("not-json")
                .contains("_sanitizer_error")
                .contains("non-json");
    }

    @Test
    void nullEBlankPreservados() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("")).isEmpty();
    }
}
