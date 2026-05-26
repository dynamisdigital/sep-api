package com.dynamis.sep_api.backoffice.infrastructure.adapter.cobranca;

import com.dynamis.sep_api.backoffice.application.dto.InadimplenciaConsolidada;
import com.dynamis.sep_api.backoffice.application.port.out.DashboardCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Adapter de fronteira — agregados de cobranca pro dashboard (Sprint 14 Task 14.5). */
@Component
public class DashboardCobrancaQueryAdapter implements DashboardCobrancaQueryPort {

    private final RecebimentoRepository recebimentoRepository;
    private final ParcelaCobrancaRepository parcelaRepository;

    public DashboardCobrancaQueryAdapter(
            RecebimentoRepository recebimentoRepository, ParcelaCobrancaRepository parcelaRepository) {
        this.recebimentoRepository = recebimentoRepository;
        this.parcelaRepository = parcelaRepository;
    }

    @Override
    public BigDecimal recebimentosNoIntervalo(OffsetDateTime inicio, OffsetDateTime fim) {
        BigDecimal total = recebimentoRepository.somarValorRecebidoNoIntervalo(inicio, fim);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Override
    public InadimplenciaConsolidada inadimplenciaTotal() {
        ParcelaCobrancaRepository.ResumoInadimplenciaView v = parcelaRepository.resumoInadimplencia();
        if (v == null) {
            return InadimplenciaConsolidada.vazia();
        }
        BigDecimal valor = v.getValorTotal() != null ? v.getValorTotal() : BigDecimal.ZERO;
        return new InadimplenciaConsolidada(valor, v.getNumeroParcelas());
    }
}
