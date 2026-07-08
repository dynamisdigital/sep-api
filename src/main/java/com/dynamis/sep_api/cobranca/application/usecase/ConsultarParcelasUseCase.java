package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.ConsultarParcelasQuery;
import com.dynamis.sep_api.cobranca.application.port.out.ParcelaCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Use case de consulta de parcelas com filtros opcionais (Sprint 12 - Task 12.5).
 */
@Service
@Transactional(readOnly = true)
public class ConsultarParcelasUseCase {

    private final ParcelaCobrancaPort parcelaPort;

    public ConsultarParcelasUseCase(ParcelaCobrancaPort parcelaPort) {
        this.parcelaPort = parcelaPort;
    }

    public List<ParcelaCobranca> executar(ConsultarParcelasQuery query) {
        List<ParcelaCobranca> base;
        if (query.contratoId() != null) {
            base = parcelaPort.listarPorContratoOrdenadoPorNumero(query.contratoId());
        } else {
            base = parcelaPort.listarTodas();
        }
        return base.stream()
                .filter(p -> query.status() == null || p.getStatus() == query.status())
                .filter(p -> query.vencimentoInicio() == null
                        || !p.getDataVencimento().isBefore(query.vencimentoInicio()))
                .filter(p ->
                        query.vencimentoFim() == null || !p.getDataVencimento().isAfter(query.vencimentoFim()))
                .sorted(Comparator.comparing(ParcelaCobranca::getDataVencimento)
                        .thenComparingInt(ParcelaCobranca::getNumero))
                .toList();
    }
}
