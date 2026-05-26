package com.dynamis.sep_api.backoffice.infrastructure.adapter.cobranca;

import com.dynamis.sep_api.backoffice.application.dto.InadimplenciaConsolidada;
import com.dynamis.sep_api.backoffice.application.port.out.DashboardCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
    public BigDecimal recebimentosNoDia(LocalDate dia) {
        OffsetDateTime inicio = dia.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime fim = dia.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        BigDecimal total = recebimentoRepository.somarValorRecebidoNoIntervalo(inicio, fim);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Override
    public InadimplenciaConsolidada inadimplenciaTotal() {
        Object[] resumo = parcelaRepository.resumoInadimplencia();
        if (resumo == null || resumo.length < 2) {
            return InadimplenciaConsolidada.vazia();
        }
        long numero = ((Number) resumo[0]).longValue();
        BigDecimal valor = resumo[1] instanceof BigDecimal v ? v : new BigDecimal(String.valueOf(resumo[1]));
        return new InadimplenciaConsolidada(valor, numero);
    }
}
