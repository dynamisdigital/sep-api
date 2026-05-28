package com.dynamis.sep_api.credores.application.service;

import com.dynamis.sep_api.credores.application.dto.OperacaoCarteiraView;
import com.dynamis.sep_api.credores.application.port.out.ConsultarCobrancaParaCarteiraCredoraPort;
import com.dynamis.sep_api.credores.application.port.out.ConsultarContratoParaCarteiraCredoraPort;
import com.dynamis.sep_api.credores.application.port.out.ContratoCarteiraView;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.domain.model.OportunidadeInvestimento;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Monta {@link OperacaoCarteiraView} combinando a operacao financiada com o snapshot da
 * oportunidade (local) e leituras cross-module de contrato e cobranca (via ports). Centraliza o
 * enriquecimento usado pelas consultas de carteira (lista e detalhe).
 */
@Service
public class OperacaoCarteiraEnricher {

    private final OportunidadeInvestimentoRepository oportunidadeRepository;
    private final ConsultarContratoParaCarteiraCredoraPort contratoPort;
    private final ConsultarCobrancaParaCarteiraCredoraPort cobrancaPort;

    public OperacaoCarteiraEnricher(
            OportunidadeInvestimentoRepository oportunidadeRepository,
            ConsultarContratoParaCarteiraCredoraPort contratoPort,
            ConsultarCobrancaParaCarteiraCredoraPort cobrancaPort) {
        this.oportunidadeRepository = oportunidadeRepository;
        this.contratoPort = contratoPort;
        this.cobrancaPort = cobrancaPort;
    }

    public OperacaoCarteiraView enriquecer(OperacaoFinanciada operacao) {
        BigDecimal valor = null;
        Integer prazoMeses = null;
        BigDecimal taxaJurosMensal = null;
        if (operacao.getOportunidadeId() != null) {
            OportunidadeInvestimento oportunidade = oportunidadeRepository
                    .findById(operacao.getOportunidadeId())
                    .orElse(null);
            if (oportunidade != null) {
                valor = oportunidade.getValor();
                prazoMeses = oportunidade.getPrazoMeses();
                taxaJurosMensal = oportunidade.getTaxaJurosMensal();
            }
        }

        String contratoStatus = contratoPort
                .consultarPorId(operacao.getContratoId())
                .map(ContratoCarteiraView::status)
                .orElse(null);

        return new OperacaoCarteiraView(
                operacao.getId(),
                operacao.getContratoId(),
                operacao.getOportunidadeId(),
                operacao.getStatus(),
                operacao.getJustificativa(),
                valor,
                prazoMeses,
                taxaJurosMensal,
                contratoStatus,
                cobrancaPort.resumoPorContrato(operacao.getContratoId()).orElse(null),
                operacao.getDataCriacao());
    }
}
