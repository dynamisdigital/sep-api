package com.dynamis.sep_api.contratos.application.service.ccb;

import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.VersaoContrato;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CcbGeneratorTest {

    private static final String HASH_VALIDO = "a".repeat(64);

    private final CcbGenerator generator = new CcbGenerator();

    @Test
    void gerar_retornaPdfNaoVazioComMagicNumber() {
        byte[] pdf = generator.gerar(template());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void gerar_textoExtraidoContemCamposMinimos() throws Exception {
        CcbTemplate template = template();

        byte[] pdf = generator.gerar(template);
        String texto = extrair(pdf);

        assertThat(texto)
                .containsIgnoringWhitespaces("CEDULA DE CREDITO BANCARIO")
                .contains(template.numero())
                .contains(template.identificacaoEmitente())
                .contains(template.identificacaoFavorecida())
                .contains(template.valorPrincipal())
                .contains(String.valueOf(template.prazoMeses()))
                .contains(template.hashVersao())
                .containsIgnoringCase("ASSINATURA");
    }

    @Test
    void gerar_textoExtraidoContemClausulasFinais() throws Exception {
        CcbTemplate template = template();

        byte[] pdf = generator.gerar(template);
        String texto = extrair(pdf);

        for (String clausula : template.clausulasFinais()) {
            assertThat(texto).contains(clausula.substring(0, Math.min(40, clausula.length())));
        }
    }

    @Test
    void de_montaTemplateAPartirDeEntidadesPersistidas() {
        PropostaCredito proposta = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("12345.67"), 24);
        Contrato contrato = Contrato.criar(proposta.getId(), proposta.getTomadorId(), TipoContrato.CCB);
        VersaoContrato versao = contrato.adicionarVersao("conteudo de teste", HASH_VALIDO);

        CcbTemplate template = CcbTemplate.de(contrato, versao, proposta);

        assertThat(template.numero()).contains(contrato.getId().toString());
        assertThat(template.valorPrincipal()).isEqualTo("12.345,67");
        assertThat(template.moeda()).isEqualTo("BRL");
        assertThat(template.prazoMeses()).isEqualTo(24);
        assertThat(template.tipoOperacao()).isEqualTo("CAPITAL_GIRO");
        assertThat(template.hashVersao()).isEqualTo(HASH_VALIDO);
        assertThat(template.numeroVersao()).isEqualTo(versao.getNumero());
    }

    private CcbTemplate template() {
        PropostaCredito proposta = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000.00"), 12);
        Contrato contrato = Contrato.criar(proposta.getId(), proposta.getTomadorId(), TipoContrato.CCB);
        VersaoContrato versao = contrato.adicionarVersao("conteudo de teste", HASH_VALIDO);
        return CcbTemplate.de(contrato, versao, proposta);
    }

    private String extrair(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
