package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.GerarSugestoesMatchingResult;
import com.dynamis.sep_api.credores.application.port.out.ConsultarContratoParaCarteiraCredoraPort;
import com.dynamis.sep_api.credores.application.port.out.ContratoCarteiraView;
import com.dynamis.sep_api.credores.application.service.CandidatoMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.application.service.ResultadoElegibilidadeMatching;
import com.dynamis.sep_api.credores.application.service.ValidadorElegibilidadeMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.event.MatchingCredoraSugeridoEvent;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.MatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.domain.model.OportunidadeInvestimento;
import com.dynamis.sep_api.credores.domain.model.PerfilCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusOperacaoFinanciada;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.MatchingCredoraOperacaoRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.PerfilCredoraRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Refresh assistido das sugestoes de matching credora-operacao (Sprint 30 Task 30.3). Disparado
 * pela listagem operacional ({@code GET /sugestoes}, Task 30.5) — nao existe job automatico e
 * nenhuma sugestao e confirmada aqui.
 *
 * <p>Garantias:
 *
 * <ul>
 *   <li><strong>Lote sem N+1</strong>: candidatas, credoras, perfis, oportunidades, matchings
 *       existentes e status de contrato (porta cross-module) sao carregados em 6 consultas em
 *       lote, independente do numero de operacoes.
 *   <li><strong>Idempotencia concorrente</strong>: as operacoes candidatas sao lidas com {@code
 *       SELECT FOR UPDATE} em ordem deterministica — refreshes simultaneos serializam e o segundo
 *       enxerga as sugestoes do primeiro (sem violacao do UNIQUE parcial V56). Par ja sugerido,
 *       confirmado ou rejeitado nao gera nova sugestao (regra da Task 30.1).
 *   <li><strong>Limite conservador</strong>: no maximo {@value #LIMITE_CANDIDATOS} operacoes por
 *       refresh, das mais antigas para as mais novas (documentado no CREDORES.md).
 *   <li><strong>Auditoria unica</strong>: {@code CREDORA_MATCHING_SUGERIDA} emitida uma vez por
 *       sugestao nova, apos o commit (listener AFTER_COMMIT), nunca para par repetido.
 * </ul>
 */
@Service
public class GerarSugestoesMatchingCredoraUseCase {

    static final int LIMITE_CANDIDATOS = 200;

    private final OperacaoFinanciadaRepository operacaoRepository;
    private final EmpresaCredoraRepository empresaCredoraRepository;
    private final PerfilCredoraRepository perfilCredoraRepository;
    private final OportunidadeInvestimentoRepository oportunidadeRepository;
    private final MatchingCredoraOperacaoRepository matchingRepository;
    private final ConsultarContratoParaCarteiraCredoraPort contratoPort;
    private final ValidadorElegibilidadeMatchingCredoraOperacao validador;
    private final ApplicationEventPublisher eventPublisher;

    public GerarSugestoesMatchingCredoraUseCase(
            OperacaoFinanciadaRepository operacaoRepository,
            EmpresaCredoraRepository empresaCredoraRepository,
            PerfilCredoraRepository perfilCredoraRepository,
            OportunidadeInvestimentoRepository oportunidadeRepository,
            MatchingCredoraOperacaoRepository matchingRepository,
            ConsultarContratoParaCarteiraCredoraPort contratoPort,
            ValidadorElegibilidadeMatchingCredoraOperacao validador,
            ApplicationEventPublisher eventPublisher) {
        this.operacaoRepository = operacaoRepository;
        this.empresaCredoraRepository = empresaCredoraRepository;
        this.perfilCredoraRepository = perfilCredoraRepository;
        this.oportunidadeRepository = oportunidadeRepository;
        this.matchingRepository = matchingRepository;
        this.contratoPort = contratoPort;
        this.validador = validador;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public GerarSugestoesMatchingResult executar(UUID atorId) {
        Objects.requireNonNull(atorId, "atorId obrigatorio");

        List<OperacaoFinanciada> operacoes = operacaoRepository.buscarAssociadasParaMatchingForUpdate(
                StatusOperacaoFinanciada.ASSOCIADA, PageRequest.of(0, LIMITE_CANDIDATOS));
        if (operacoes.isEmpty()) {
            return new GerarSugestoesMatchingResult(0);
        }

        List<CandidatoMatchingCredoraOperacao> candidatos = montarCandidatos(operacoes);

        List<MatchingCredoraOperacao> novas = new ArrayList<>();
        for (CandidatoMatchingCredoraOperacao candidato : candidatos) {
            ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato);
            if (resultado.elegivel()) {
                novas.add(MatchingCredoraOperacao.sugerir(
                        candidato.empresaCredoraId(),
                        candidato.operacaoId(),
                        candidato.valorOperacao(),
                        resultado.criteriosAtendidos()));
            }
        }
        if (novas.isEmpty()) {
            return new GerarSugestoesMatchingResult(0);
        }

        // Instancias retornadas pelo saveAll (managed) alimentam os eventos — com id atribuido na
        // factory o save faz merge, e a instancia original (detached) nao reflete a persistencia.
        List<MatchingCredoraOperacao> salvas = matchingRepository.saveAll(novas);
        for (MatchingCredoraOperacao salva : salvas) {
            eventPublisher.publishEvent(new MatchingCredoraSugeridoEvent(
                    salva.getId(),
                    salva.getOperacaoId(),
                    salva.getEmpresaCredoraId(),
                    salva.getValorElegivel(),
                    atorId));
        }
        return new GerarSugestoesMatchingResult(salvas.size());
    }

    /** Monta o snapshot de cada par com 5 leituras em lote (credora, perfil, oportunidade, matching, contrato). */
    private List<CandidatoMatchingCredoraOperacao> montarCandidatos(List<OperacaoFinanciada> operacoes) {
        Set<UUID> credoraIds =
                operacoes.stream().map(OperacaoFinanciada::getEmpresaCredoraId).collect(Collectors.toSet());
        Set<UUID> contratoIds =
                operacoes.stream().map(OperacaoFinanciada::getContratoId).collect(Collectors.toSet());
        Set<UUID> oportunidadeIds = operacoes.stream()
                .map(OperacaoFinanciada::getOportunidadeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<UUID> operacaoIds =
                operacoes.stream().map(OperacaoFinanciada::getId).toList();

        Map<UUID, EmpresaCredora> credoras = empresaCredoraRepository.findAllById(credoraIds).stream()
                .collect(Collectors.toMap(EmpresaCredora::getId, Function.identity()));
        Map<UUID, PerfilCredora> perfis = perfilCredoraRepository.findAllByEmpresaCredoraIdIn(credoraIds).stream()
                .collect(Collectors.toMap(PerfilCredora::getEmpresaCredoraId, Function.identity()));
        Map<UUID, OportunidadeInvestimento> oportunidades = oportunidadeRepository.findAllById(oportunidadeIds).stream()
                .collect(Collectors.toMap(OportunidadeInvestimento::getId, Function.identity()));
        Set<UUID> operacoesComMatching = matchingRepository.findAllByOperacaoIdIn(operacaoIds).stream()
                .map(MatchingCredoraOperacao::getOperacaoId)
                .collect(Collectors.toSet());
        Map<UUID, String> statusContratos = contratoPort.consultarPorIds(contratoIds).stream()
                .collect(Collectors.toMap(ContratoCarteiraView::contratoId, ContratoCarteiraView::status));

        List<CandidatoMatchingCredoraOperacao> candidatos = new ArrayList<>(operacoes.size());
        for (OperacaoFinanciada operacao : operacoes) {
            EmpresaCredora credora = credoras.get(operacao.getEmpresaCredoraId());
            if (credora == null) {
                continue; // FK garante presenca; defensivo contra dados insuficientes
            }
            PerfilCredora perfil = perfis.get(operacao.getEmpresaCredoraId());
            OportunidadeInvestimento oportunidade =
                    operacao.getOportunidadeId() == null ? null : oportunidades.get(operacao.getOportunidadeId());
            candidatos.add(new CandidatoMatchingCredoraOperacao(
                    credora.getId(),
                    operacao.getId(),
                    credora.getStatus(),
                    credora.getElegibilidade(),
                    perfil == null ? null : perfil.getCapacidadeAporte(),
                    operacao.getStatus(),
                    statusContratos.get(operacao.getContratoId()),
                    oportunidade == null ? null : oportunidade.getValor(),
                    operacoesComMatching.contains(operacao.getId())));
        }
        return candidatos;
    }
}
