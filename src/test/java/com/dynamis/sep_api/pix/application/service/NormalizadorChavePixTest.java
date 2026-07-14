package com.dynamis.sep_api.pix.application.service;

import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizadorChavePixTest {

    // --- CPF ---

    @Test
    void cpf_removePontuacaoEValida() {
        assertThat(NormalizadorChavePix.normalizar(TipoChavePix.CPF, "123.456.789-09"))
                .isEqualTo("12345678909");
    }

    @Test
    void cpf_jaNormalizado_permanece() {
        assertThat(NormalizadorChavePix.normalizar(TipoChavePix.CPF, " 12345678909 "))
                .isEqualTo("12345678909");
    }

    @ParameterizedTest
    @CsvSource({"1234567890", "123456789012", "1234567890a"})
    void cpf_invalido_rejeitaSemEcoarValor(String valor) {
        assertThatThrownBy(() -> NormalizadorChavePix.normalizar(TipoChavePix.CPF, valor))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(valor));
    }

    @ParameterizedTest
    @CsvSource({"12345678901", "52998224726", "00000000000", "11111111111", "99999999999"})
    void cpf_digitoVerificadorInvalidoOuSequenciaRepetida_rejeita(String valor) {
        assertThatThrownBy(() -> NormalizadorChavePix.normalizar(TipoChavePix.CPF, valor))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(valor));
    }

    @Test
    void cpf_comDigitoVerificadorValido_aceita() {
        assertThat(NormalizadorChavePix.normalizar(TipoChavePix.CPF, "529.982.247-25"))
                .isEqualTo("52998224725");
    }

    // --- CNPJ ---

    @Test
    void cnpj_removePontuacaoEValida() {
        assertThat(NormalizadorChavePix.normalizar(TipoChavePix.CNPJ, "12.345.678/0001-95"))
                .isEqualTo("12345678000195");
    }

    @Test
    void cnpj_tamanhoErrado_rejeita() {
        assertThatThrownBy(() -> NormalizadorChavePix.normalizar(TipoChavePix.CNPJ, "12345678000"))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("12345678000"));
    }

    @ParameterizedTest
    @CsvSource({"12345678000190", "11222333000182", "00000000000000", "11111111111111"})
    void cnpj_digitoVerificadorInvalidoOuSequenciaRepetida_rejeita(String valor) {
        assertThatThrownBy(() -> NormalizadorChavePix.normalizar(TipoChavePix.CNPJ, valor))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(valor));
    }

    @Test
    void cnpj_comDigitoVerificadorValido_aceita() {
        assertThat(NormalizadorChavePix.normalizar(TipoChavePix.CNPJ, "11.222.333/0001-81"))
                .isEqualTo("11222333000181");
    }

    // --- TELEFONE ---

    @Test
    void telefone_comFormatacao_normalizaParaE164() {
        assertThat(NormalizadorChavePix.normalizar(TipoChavePix.TELEFONE, "+55 (11) 99999-8888"))
                .isEqualTo("+5511999998888");
    }

    @Test
    void telefone_semDdi_prefixaBrasil() {
        assertThat(NormalizadorChavePix.normalizar(TipoChavePix.TELEFONE, "11999998888"))
                .isEqualTo("+5511999998888");
    }

    @Test
    void telefone_fixoSemDdi_prefixaBrasil() {
        assertThat(NormalizadorChavePix.normalizar(TipoChavePix.TELEFONE, "1133334444"))
                .isEqualTo("+551133334444");
    }

    @ParameterizedTest
    @CsvSource({"123", "+5511abc998888", "+1 555 0100", "0099998888", "+55 (00) 9999-8888", "1099998888"})
    void telefone_invalido_rejeitaSemEcoarValor(String valor) {
        assertThatThrownBy(() -> NormalizadorChavePix.normalizar(TipoChavePix.TELEFONE, valor))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(valor));
    }

    // --- EMAIL ---

    @Test
    void email_trimELowercase() {
        assertThat(NormalizadorChavePix.normalizar(TipoChavePix.EMAIL, "  Usuario@Empresa.COM  "))
                .isEqualTo("usuario@empresa.com");
    }

    @ParameterizedTest
    @CsvSource({
        "sem-arroba",
        "dois@@empresa.com",
        "@empresa.com",
        "usuario@",
        "usuario@empresa..com",
        "usuario@.com",
        "usuario..nome@empresa.com"
    })
    void email_invalido_rejeitaSemEcoarValor(String valor) {
        assertThatThrownBy(() -> NormalizadorChavePix.normalizar(TipoChavePix.EMAIL, valor))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(valor));
    }

    @Test
    void email_acimaDoLimiteDict_rejeita() {
        String longo = "a".repeat(70) + "@empresa.com";
        assertThatThrownBy(() -> NormalizadorChavePix.normalizar(TipoChavePix.EMAIL, longo))
                .isInstanceOf(ValidacaoException.class);
    }

    // --- EVP ---

    @Test
    void evp_normalizaParaUuidCanonicoLowercase() {
        assertThat(NormalizadorChavePix.normalizar(TipoChavePix.EVP, " 123E4567-E89B-12D3-A456-426614174000 "))
                .isEqualTo("123e4567-e89b-12d3-a456-426614174000");
    }

    @ParameterizedTest
    @CsvSource({"nao-e-uuid", "123e4567e89b12d3a456426614174000"})
    void evp_invalida_rejeitaSemEcoarValor(String valor) {
        assertThatThrownBy(() -> NormalizadorChavePix.normalizar(TipoChavePix.EVP, valor))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(valor));
    }

    // --- regras transversais ---

    @Test
    void hash_igualParaRepresentacoesEquivalentes() {
        String h1 = ChavePixSeguranca.hashHex(NormalizadorChavePix.normalizar(TipoChavePix.CPF, "123.456.789-09"));
        String h2 = ChavePixSeguranca.hashHex(NormalizadorChavePix.normalizar(TipoChavePix.CPF, "12345678909"));

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void mascara_sobreValorNormalizado_nuncaRetornaValorIntegral() {
        String normalizado = NormalizadorChavePix.normalizar(TipoChavePix.EMAIL, "usuario@empresa.com");
        String mascara = ChavePixSeguranca.mascarar(normalizado);

        assertThat(mascara).isNotEqualTo(normalizado).contains("*").hasSizeLessThanOrEqualTo(80);
    }

    @Test
    void tipoNulo_rejeita() {
        assertThatThrownBy(() -> NormalizadorChavePix.normalizar(null, "12345678909"))
                .isInstanceOf(ValidacaoException.class);
    }

    @Test
    void valorNuloOuVazio_rejeita() {
        assertThatThrownBy(() -> NormalizadorChavePix.normalizar(TipoChavePix.CPF, null))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> NormalizadorChavePix.normalizar(TipoChavePix.CPF, "   "))
                .isInstanceOf(ValidacaoException.class);
    }
}
