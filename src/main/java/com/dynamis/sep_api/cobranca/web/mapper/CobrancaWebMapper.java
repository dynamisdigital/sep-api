package com.dynamis.sep_api.cobranca.web.mapper;

import com.dynamis.sep_api.cobranca.application.dto.ParcelaAtualizadaResult;
import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoResult;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import com.dynamis.sep_api.cobranca.web.dto.AgendaPagamentoResponse;
import com.dynamis.sep_api.cobranca.web.dto.ParcelaResponse;
import com.dynamis.sep_api.cobranca.web.dto.RecebimentoResponse;
import com.dynamis.sep_api.cobranca.web.dto.ValorAtualizadoParcelaResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper manual entre entidades/results do dominio cobranca e DTOs REST (Sprint 12 Task 12.6).
 * Manual em vez de MapStruct pra evitar override de records — o overhead nao justifica gerar
 * codigo aqui.
 */
@Component
public class CobrancaWebMapper {

    public AgendaPagamentoResponse toAgendaResponse(AgendaPagamento agenda) {
        return new AgendaPagamentoResponse(
                agenda.getId(),
                agenda.getContratoId(),
                agenda.getNumeroParcelas(),
                agenda.getValorTotal(),
                agenda.getDataGeracao(),
                agenda.getParcelas().stream().map(this::toParcelaResponse).toList());
    }

    public ParcelaResponse toParcelaResponse(ParcelaCobranca parcela) {
        return new ParcelaResponse(
                parcela.getId(),
                parcela.getNumero(),
                parcela.getPrincipal(),
                parcela.getJuros(),
                parcela.getMulta(),
                parcela.getEncargos(),
                parcela.valorTotal(),
                parcela.getDataVencimento(),
                parcela.getStatus());
    }

    public ValorAtualizadoParcelaResponse toValorAtualizadoResponse(ParcelaAtualizadaResult r) {
        return new ValorAtualizadoParcelaResponse(
                r.parcelaId(),
                r.numero(),
                r.status(),
                r.dataVencimento(),
                r.composicaoOriginal().principal(),
                r.composicaoOriginal().juros(),
                r.jurosMora(),
                r.multa(),
                r.valorDevidoAtualizado(),
                r.totalRecebido(),
                r.valorEmAberto());
    }

    public RecebimentoResponse toRecebimentoResponse(RegistrarRecebimentoResult result) {
        return new RecebimentoResponse(
                result.recebimentoId(),
                result.parcelaId(),
                result.statusParcela(),
                result.valorRecebido(),
                result.dataRecebimento(),
                result.meioPagamento(),
                result.identificadorExterno(),
                result.movimentacaoEscrowId(),
                result.novo());
    }

    public RecebimentoResponse toRecebimentoResponse(Recebimento recebimento) {
        return new RecebimentoResponse(
                recebimento.getId(),
                recebimento.getParcela().getId(),
                recebimento.getParcela().getStatus(),
                recebimento.getValorRecebido(),
                recebimento.getDataRecebimento(),
                recebimento.getMeioPagamento(),
                recebimento.getIdentificadorExterno(),
                null,
                false);
    }

    public List<RecebimentoResponse> toRecebimentoListResponse(List<Recebimento> recebimentos) {
        return recebimentos.stream().map(this::toRecebimentoResponse).toList();
    }
}
