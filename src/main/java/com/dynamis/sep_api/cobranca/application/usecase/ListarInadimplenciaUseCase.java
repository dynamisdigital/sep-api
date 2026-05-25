package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lista parcelas em situacao de inadimplencia (Sprint 13 Task 13.7) para o backoffice/financeiro.
 *
 * <p>Suporta filtros por status, dias minimos/maximos de atraso. Resolve tomadorId via
 * {@link ContratoCobrancaQueryPort} para evitar dependencia direta no modulo de contratos.
 *
 * <p><strong>Conhecido (Sprint 14):</strong> resolve tomadorId em loop ({@code N+1}). Aceitavel
 * pra volume MVP (dezenas/centenas de parcelas em atraso). Pra produçao com milhares de parcelas,
 * substituir por join SQL + paginacao (offset/limit ou cursor). Mesma estrategia aplicada em
 * {@code CobrancaController.listarInadimplencia} apos Sprint 14 (Backoffice).
 */
@Service
public class ListarInadimplenciaUseCase {

    private static final Set<StatusParcela> STATUS_INADIMPLENCIA =
            EnumSet.of(StatusParcela.ATRASADA, StatusParcela.INADIMPLENTE);

    private final ParcelaCobrancaRepository parcelaRepository;
    private final ContratoCobrancaQueryPort contratoQuery;
    private final Clock clock;

    public ListarInadimplenciaUseCase(
            ParcelaCobrancaRepository parcelaRepository, ContratoCobrancaQueryPort contratoQuery, Clock clock) {
        this.parcelaRepository = parcelaRepository;
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
        List<ParcelaCobranca> parcelas = parcelaRepository.findByStatusInOrderByDataVencimentoAsc(filtros);
        List<LinhaInadimplencia> resultado = new ArrayList<>();
        for (ParcelaCobranca parcela : parcelas) {
            int dias = (int) ChronoUnit.DAYS.between(parcela.getDataVencimento(), hoje);
            if (filtro.diasAtrasoMin() != null && dias < filtro.diasAtrasoMin()) {
                continue;
            }
            if (filtro.diasAtrasoMax() != null && dias > filtro.diasAtrasoMax()) {
                continue;
            }
            UUID contratoId = parcela.getAgenda().getContratoId();
            UUID tomadorId = contratoQuery.tomadorIdDoContrato(contratoId).orElse(null);
            resultado.add(new LinhaInadimplencia(parcela, contratoId, tomadorId, dias));
        }
        return resultado;
    }

    public record Filtro(Set<StatusParcela> status, Integer diasAtrasoMin, Integer diasAtrasoMax) {}

    public record LinhaInadimplencia(ParcelaCobranca parcela, UUID contratoId, UUID tomadorId, int diasAtraso) {}
}
