package com.dynamis.sep_api.contratos.application.service.ccb;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gera a Cedula de Credito Bancario em PDF estruturado (Sprint 11 Task 11.3).
 *
 * <p>Saida: {@code byte[]} contendo PDF com texto pesquisavel (nao imagem) — exigencia
 * regulatoria de integridade da Lei 10.931/2004. Conteudo deriva exclusivamente do {@link
 * CcbTemplate} construido a partir de dados ja persistidos; falha de geracao propaga {@link
 * CcbGeracaoException} para abortar envio para o provider de assinatura digital.
 *
 * <p>Fontes: Helvetica (Standard 14 Font) embutida pelo proprio PDFBox, sem dependencia externa.
 * Tamanhos: titulo 16pt bold, secao 12pt bold, corpo 10pt regular, rodape 8pt regular.
 */
@Component
public class CcbGenerator {

    private static final float MARGEM_HORIZONTAL = 50f;
    private static final float MARGEM_TOPO = 750f;
    private static final float MARGEM_INFERIOR = 80f;
    private static final float LARGURA_LINHA = PDRectangle.A4.getWidth() - (MARGEM_HORIZONTAL * 2);

    private static final float TAM_TITULO = 16f;
    private static final float TAM_SECAO = 12f;
    private static final float TAM_CORPO = 10f;
    private static final float TAM_RODAPE = 8f;

    private static final float ENTRELINHA_CORPO = 14f;
    private static final float ENTRELINHA_SECAO = 18f;
    private static final float ESPACO_APOS_SECAO = 8f;

    /** Gera o PDF da CCB a partir do template. */
    public byte[] gerar(CcbTemplate template) {
        try (PDDocument doc = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            renderizar(doc, template);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new CcbGeracaoException("Falha ao gerar PDF da CCB", e);
        }
    }

    private void renderizar(PDDocument doc, CcbTemplate t) throws IOException {
        PDPage pagina = new PDPage(PDRectangle.A4);
        doc.addPage(pagina);

        Cursor c = new Cursor(MARGEM_TOPO);
        try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
            escreverTitulo(cs, c, "CEDULA DE CREDITO BANCARIO");
            escreverLinha(cs, c, "Numero: " + t.numero(), TAM_CORPO, false);
            escreverLinha(cs, c, "Data de Emissao: " + t.dataEmissao(), TAM_CORPO, false);
            escreverLinha(cs, c, "Versao: " + t.numeroVersao(), TAM_CORPO, false);
            espacar(c, ENTRELINHA_CORPO);

            escreverSecao(cs, c, "I. PARTES");
            escreverLinha(cs, c, "Emitente (Tomador): " + t.identificacaoEmitente(), TAM_CORPO, false);
            escreverLinha(cs, c, "Favorecida (Credor): " + t.identificacaoFavorecida(), TAM_CORPO, false);
            espacar(c, ESPACO_APOS_SECAO);

            escreverSecao(cs, c, "II. VALOR PRINCIPAL E CONDICOES");
            escreverLinha(cs, c, "Tipo de operacao: " + t.tipoOperacao(), TAM_CORPO, false);
            escreverLinha(cs, c, "Valor principal: " + t.moeda() + " " + t.valorPrincipal(), TAM_CORPO, false);
            escreverLinha(cs, c, "Prazo: " + t.prazoMeses() + " meses", TAM_CORPO, false);
            espacar(c, ESPACO_APOS_SECAO);

            escreverSecao(cs, c, "III. FORMA DE PAGAMENTO");
            quebrarTexto(cs, c, t.formaPagamento(), TAM_CORPO);
            espacar(c, ESPACO_APOS_SECAO);

            escreverSecao(cs, c, "IV. GARANTIAS");
            quebrarTexto(cs, c, t.garantias(), TAM_CORPO);
            espacar(c, ESPACO_APOS_SECAO);

            escreverSecao(cs, c, "V. FORO");
            quebrarTexto(cs, c, t.foro(), TAM_CORPO);
            espacar(c, ESPACO_APOS_SECAO);

            escreverSecao(cs, c, "VI. CLAUSULAS FINAIS");
            for (String clausula : t.clausulasFinais()) {
                quebrarTexto(cs, c, "- " + clausula, TAM_CORPO);
            }
            espacar(c, ENTRELINHA_CORPO);

            escreverSecao(cs, c, "VII. ASSINATURA DIGITAL");
            quebrarTexto(
                    cs,
                    c,
                    "Espaco reservado para assinatura eletronica/avancada nos termos da Lei 14.063/2020.",
                    TAM_CORPO);
            espacar(c, ENTRELINHA_CORPO);

            escreverRodape(cs, c, "Hash da versao (SHA-256): " + t.hashVersao());
        }
    }

    private void escreverTitulo(PDPageContentStream cs, Cursor c, String texto) throws IOException {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), TAM_TITULO);
        cs.newLineAtOffset(MARGEM_HORIZONTAL, c.y);
        cs.showText(texto);
        cs.endText();
        c.y -= ENTRELINHA_SECAO + 6f;
    }

    private void escreverSecao(PDPageContentStream cs, Cursor c, String texto) throws IOException {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), TAM_SECAO);
        cs.newLineAtOffset(MARGEM_HORIZONTAL, c.y);
        cs.showText(texto);
        cs.endText();
        c.y -= ENTRELINHA_SECAO;
    }

    private void escreverLinha(PDPageContentStream cs, Cursor c, String texto, float tamanho, boolean bold)
            throws IOException {
        cs.beginText();
        cs.setFont(
                new PDType1Font(bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA),
                tamanho);
        cs.newLineAtOffset(MARGEM_HORIZONTAL, c.y);
        cs.showText(sanitizar(texto));
        cs.endText();
        c.y -= ENTRELINHA_CORPO;
    }

    private void escreverRodape(PDPageContentStream cs, Cursor c, String texto) throws IOException {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), TAM_RODAPE);
        cs.newLineAtOffset(MARGEM_HORIZONTAL, Math.max(c.y, MARGEM_INFERIOR));
        cs.showText(sanitizar(texto));
        cs.endText();
    }

    private void quebrarTexto(PDPageContentStream cs, Cursor c, String texto, float tamanho) throws IOException {
        for (String linha : dividirEmLinhas(texto, tamanho)) {
            escreverLinha(cs, c, linha, tamanho, false);
        }
    }

    private void espacar(Cursor c, float quanto) {
        c.y -= quanto;
    }

    /**
     * Quebra texto longo em linhas que cabem na largura util da pagina. Usa media empirica de
     * 5pt/char para Helvetica em corpo 10pt — suficiente para CCB com vocabulario tecnico curto.
     */
    private List<String> dividirEmLinhas(String texto, float tamanho) {
        int maxChars = (int) (LARGURA_LINHA / (tamanho * 0.5f));
        List<String> linhas = new ArrayList<>();
        String[] palavras = texto.split(" ");
        StringBuilder atual = new StringBuilder();
        for (String palavra : palavras) {
            if (atual.length() + palavra.length() + 1 > maxChars) {
                if (!atual.isEmpty()) {
                    linhas.add(atual.toString());
                    atual.setLength(0);
                }
            }
            if (!atual.isEmpty()) {
                atual.append(' ');
            }
            atual.append(palavra);
        }
        if (!atual.isEmpty()) {
            linhas.add(atual.toString());
        }
        return linhas;
    }

    /**
     * Remove caracteres que PDFBox Standard 14 Helvetica nao consegue codificar (ex.: emoji,
     * caracteres de controle). Mantem acentos pt-BR (suportados pelo WinAnsiEncoding).
     */
    private String sanitizar(String texto) {
        return texto.replaceAll("[\\p{Cntrl}\\p{So}\\p{Cs}]", "");
    }

    /** Cursor vertical mutavel (PDFBox Y cresce pra cima; comecamos no topo e descemos). */
    private static final class Cursor {
        float y;

        Cursor(float y) {
            this.y = y;
        }
    }
}
