package com.dynamis.sep_api.contratos.application.service.ccb;

import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.VersaoContrato;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Modelo logico da Cedula de Credito Bancario (Sprint 11 Task 11.3).
 *
 * <p>Carrega o conteudo que sera renderizado em PDF pelo {@link CcbGenerator}. O template e
 * construido a partir do contrato vigente aceito, da proposta de credito aprovada e da versao
 * aceita do contrato — somente dados ja persistidos sao usados (sem inventar dados cadastrais
 * ausentes; identificacao das partes usa UUIDs ate onboarding expor nome/CNPJ completos,
 * pendencia rastreada em CONTRATOS.md/CCB.md).
 *
 * <p>Conteudo regulado pela Lei 10.931/2004 (titulo executivo extrajudicial). Estrutura minima:
 * cabecalho + partes + valor/prazo/encargos + forma de pagamento + garantias + foro + reserva
 * para assinatura digital.
 */
public record CcbTemplate(
        String numero,
        String dataEmissao,
        String identificacaoEmitente,
        String identificacaoFavorecida,
        String valorPrincipal,
        String moeda,
        int prazoMeses,
        String tipoOperacao,
        String formaPagamento,
        String garantias,
        String foro,
        List<String> clausulasFinais,
        String hashVersao,
        int numeroVersao) {

    private static final Locale LOCALE_PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_PT_BR);

    private static final String FAVORECIDA_PADRAO = "SEP - Sociedade de Emprestimo entre Pessoas (Dynamis Digital)";
    private static final String FORO_PADRAO = "Comarca de Sao Paulo / SP";
    private static final List<String> CLAUSULAS_FINAIS_PADRAO = List.of(
            "Esta Cedula de Credito Bancario constitui titulo executivo extrajudicial nos termos da Lei 10.931/2004.",
            "A assinatura digital aposta nesta CCB possui validade juridica conforme a Lei 14.063/2020.",
            "O pagamento sera realizado por meio da conta escrow operada pela plataforma, conforme Resolucao CMN 4.656/2018.");

    public CcbTemplate {
        Objects.requireNonNull(numero, "numero obrigatorio");
        Objects.requireNonNull(dataEmissao, "dataEmissao obrigatoria");
        Objects.requireNonNull(identificacaoEmitente, "identificacaoEmitente obrigatoria");
        Objects.requireNonNull(identificacaoFavorecida, "identificacaoFavorecida obrigatoria");
        Objects.requireNonNull(valorPrincipal, "valorPrincipal obrigatorio");
        Objects.requireNonNull(moeda, "moeda obrigatoria");
        Objects.requireNonNull(tipoOperacao, "tipoOperacao obrigatorio");
        Objects.requireNonNull(formaPagamento, "formaPagamento obrigatoria");
        Objects.requireNonNull(garantias, "garantias obrigatoria (use string vazia se nao houver)");
        Objects.requireNonNull(foro, "foro obrigatorio");
        Objects.requireNonNull(clausulasFinais, "clausulasFinais obrigatorias");
        Objects.requireNonNull(hashVersao, "hashVersao obrigatorio");
    }

    /**
     * Monta o template a partir das entidades persistidas. {@code dataEmissao} e parametro
     * obrigatorio (nao usar {@code OffsetDateTime.now()} interno) para garantir geracao
     * deterministica do PDF — exigencia da integridade do hash em {@code
     * EnvelopeAssinatura.hashPdfEnviado} e da idempotencia de reenvio (Task 11.5). O caller deve
     * derivar {@code dataEmissao} da {@code VersaoContrato.dataGeracao} ou de relogio injetado.
     */
    public static CcbTemplate de(
            Contrato contrato, VersaoContrato versao, PropostaCredito proposta, OffsetDateTime dataEmissao) {
        Objects.requireNonNull(contrato, "contrato obrigatorio");
        Objects.requireNonNull(versao, "versao obrigatoria");
        Objects.requireNonNull(proposta, "proposta obrigatoria");
        Objects.requireNonNull(dataEmissao, "dataEmissao obrigatoria");
        return new CcbTemplate(
                "CCB-" + contrato.getId(),
                FORMATO_DATA.format(dataEmissao),
                "Tomador UUID " + contrato.getTomadorId(),
                FAVORECIDA_PADRAO,
                formatarValor(proposta.getValorSolicitado()),
                proposta.getMoeda(),
                proposta.getPrazoMeses(),
                proposta.getTipoOperacao().name(),
                "Debito automatico em conta escrow vinculada ao contrato",
                "Sem garantias reais nesta versao",
                FORO_PADRAO,
                CLAUSULAS_FINAIS_PADRAO,
                versao.getHashSha256(),
                versao.getNumero());
    }

    private static String formatarValor(java.math.BigDecimal valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(LOCALE_PT_BR);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor);
    }
}
