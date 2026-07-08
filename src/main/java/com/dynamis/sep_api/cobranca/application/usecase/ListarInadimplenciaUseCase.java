package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.port.out.ParcelaCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lista parcelas em situacao de inadimplencia (Sprint 13 Task 13.7) para o backoffice/financeiro.
 *
 * <p>Suporta filtros por status, dias minimos/maximos de atraso. Resolve tomadorId via
 * {@link ContratoCobrancaQueryPort} para evitar dependencia direta no modulo de contratos.
 *
 * <p><strong>Sprint 15 Task 15.4 (15F-002):</strong> resolve tomadorId em lote via
 * {@link ContratoCobrancaQueryPort#tomadoresPorContratoIds(java.util.Collection)} — 1 query por
 * listagem em vez de N. Paginacao continua em followup (volume MVP atual: dezenas/centenas).
 */
@Service
public class ListarInadimplenciaUseCase {

    private static final Set<StatusParcela> STATUS_INADIMPLENCIA =
            EnumSet.of(StatusParcela.ATRASADA, StatusParcela.INADIMPLENTE);

    private final ParcelaCobrancaPort parcelaPort;
    private final ContratoCobrancaQueryPort contratoQuery;
    private final Clock clock;

    public ListarInadimplenciaUseCase(
            ParcelaCobrancaPort parcelaPort, ContratoCobrancaQueryPort contratoQuery, Clock clock) {
        this.parcelaPort = parcelaPort;
        this.contratoQuery = contratoQuery;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LinhaInadimplencia> listar(Filtro filtro) {
        LocalDate hoje = LocalDate.now(clock);
        // Fix review manual Task 13.7: filtro restrito a STATUS_INADIMPLENCIA — endpoint nao pode
        // virar listagem genérica de parcelas (PAGA/PENDENTE/EM_NEGOCIACAO/RENEGOCIADA sao
        // legítimas em outras consultas, mas nao em "inadimplencia"). Status do filtro vira
        // interseccao com o conjunto seguro.
        Set<StatusParcela> filtros;
        if (filtro.status() == null || filtro.status().isEmpty()) {
            filtros = STATUS_INADIMPLENCIA;
        } else {
            filtros = EnumSet.copyOf(filtro.status());
            filtros.retainAll(STATUS_INADIMPLENCIA);
        }
        if (filtros.isEmpty()) {
            return List.of();
        }
        List<ParcelaCobranca> parcelas = parcelaPort.listarPorStatusOrdenadoPorVencimento(filtros);

        // Sprint 15 Task 15.4 (15F-002): coleta contratoIds dos itens que passam pelo filtro de
        // dias e resolve tomadores em UMA query (em vez de uma por iteracao). Cacheia (parcela,
        // dias) pra evitar recalculo de ChronoUnit.DAYS na segunda passada.
        List<ParcelaElegivel> elegiveis = new ArrayList<>(parcelas.size());
        Set<UUID> contratoIds = new LinkedHashSet<>();
        for (ParcelaCobranca parcela : parcelas) {
            int dias = (int) ChronoUnit.DAYS.between(parcela.getDataVencimento(), hoje);
            if (filtro.diasAtrasoMin() != null && dias < filtro.diasAtrasoMin()) {
                continue;
            }
            if (filtro.diasAtrasoMax() != null && dias > filtro.diasAtrasoMax()) {
                continue;
            }
            elegiveis.add(new ParcelaElegivel(parcela, dias));
            contratoIds.add(parcela.getAgenda().getContratoId());
        }

        Map<UUID, UUID> tomadores = contratoQuery.tomadoresPorContratoIds(contratoIds);
        List<LinhaInadimplencia> resultado = new ArrayList<>(elegiveis.size());
        for (ParcelaElegivel e : elegiveis) {
            UUID contratoId = e.parcela().getAgenda().getContratoId();
            resultado.add(new LinhaInadimplencia(e.parcela(), contratoId, tomadores.get(contratoId), e.dias()));
        }
        return resultado;
    }

    private record ParcelaElegivel(ParcelaCobranca parcela, int dias) {}

    public record Filtro(Set<StatusParcela> status, Integer diasAtrasoMin, Integer diasAtrasoMax) {}

    public record LinhaInadimplencia(ParcelaCobranca parcela, UUID contratoId, UUID tomadorId, int diasAtraso) {}
}
