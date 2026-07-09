package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.AporteCredoraView;
import com.dynamis.sep_api.credores.domain.exception.AporteOperacaoNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.infrastructure.persistence.AporteCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consulta owner-scoped dos aportes de uma operacao financiada (Sprint 29 Task 29.6). Read-only,
 * sem step-up.
 *
 * <p>Dois escopos de leitura:
 *
 * <ul>
 *   <li><strong>Visao operacional</strong> (financeiro/admin): resolve a operacao diretamente por
 *       id; inexistente lanca 404 neutro.
 *   <li><strong>Visao da credora dona</strong> (sem role dedicada — acesso por presenca de credora,
 *       padrao Sprint 26): resolve a credora do usuario e exige posse da operacao ANTES de listar.
 *       Usuario sem credora, operacao de outra credora e operacao inexistente lancam a mesma {@link
 *       AporteOperacaoNaoEncontradaException} (404 neutro sem UUID) — indistinguiveis, sem
 *       enumeracao.
 * </ul>
 *
 * <p>Lista vazia e resposta valida quando a operacao existe no escopo autorizado. Ordenacao por
 * criacao decrescente (padrao do modulo — ex.: carteira da credora).
 */
@Service
@Transactional(readOnly = true)
public class ConsultarAportesOperacaoUseCase {

    private final OperacaoFinanciadaRepository operacaoRepository;
    private final EmpresaCredoraRepository empresaRepository;
    private final AporteCredoraRepository aporteRepository;

    public ConsultarAportesOperacaoUseCase(
            OperacaoFinanciadaRepository operacaoRepository,
            EmpresaCredoraRepository empresaRepository,
            AporteCredoraRepository aporteRepository) {
        this.operacaoRepository = operacaoRepository;
        this.empresaRepository = empresaRepository;
        this.aporteRepository = aporteRepository;
    }

    public List<AporteCredoraView> executar(UUID usuarioId, UUID operacaoId, boolean visaoOperacional) {
        OperacaoFinanciada operacao =
                visaoOperacional ? resolverOperacional(operacaoId) : resolverComoCredoraDona(usuarioId, operacaoId);
        return aporteRepository.findByOperacaoIdOrderByDataCriacaoDesc(operacao.getId()).stream()
                .map(AporteCredoraView::de)
                .toList();
    }

    private OperacaoFinanciada resolverOperacional(UUID operacaoId) {
        return operacaoRepository.findById(operacaoId).orElseThrow(AporteOperacaoNaoEncontradaException::new);
    }

    private OperacaoFinanciada resolverComoCredoraDona(UUID usuarioId, UUID operacaoId) {
        EmpresaCredora credora =
                empresaRepository.findByUsuarioId(usuarioId).orElseThrow(AporteOperacaoNaoEncontradaException::new);
        return operacaoRepository
                .findByIdAndEmpresaCredoraId(operacaoId, credora.getId())
                .orElseThrow(AporteOperacaoNaoEncontradaException::new);
    }
}
