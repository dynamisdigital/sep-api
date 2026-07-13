package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.AcaoDecisaoMatching;
import com.dynamis.sep_api.credores.application.dto.DecidirMatchingCredoraOperacaoCommand;
import com.dynamis.sep_api.credores.application.dto.MatchingCredoraOperacaoView;
import com.dynamis.sep_api.credores.domain.event.MatchingCredoraConfirmadoEvent;
import com.dynamis.sep_api.credores.domain.event.MatchingCredoraRejeitadoEvent;
import com.dynamis.sep_api.credores.domain.exception.MatchingDecisaoConflitanteException;
import com.dynamis.sep_api.credores.domain.exception.MatchingNaoEncontradoException;
import com.dynamis.sep_api.credores.domain.model.MatchingCredoraOperacao;
import com.dynamis.sep_api.credores.infrastructure.persistence.MatchingCredoraOperacaoRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisao assistida do matching credora-operacao (Sprint 30 Task 30.4). Executada por
 * financeiro/admin — step-up estrito fica na borda REST (Task 30.5), padrao do aporte.
 *
 * <p>Garantias:
 *
 * <ul>
 *   <li><strong>404 neutro</strong>: sugestao inexistente lanca excecao generica sem UUID.
 *   <li><strong>Serializacao</strong>: leitura com {@code SELECT FOR UPDATE} — decisoes
 *       concorrentes sobre a mesma sugestao serializam e a segunda recebe {@code 409} (status
 *       terminal), nunca decisao dupla nem auditoria duplicada.
 *   <li><strong>Vinculo sem duplicar aporte</strong>: a confirmacao apenas persiste a decisao no
 *       matching (referencia confirmada do par credora-operacao). Nenhum {@code AporteCredora} e
 *       criado, nenhum escrow/provider e chamado — o aporte continua fluxo separado da Sprint 29,
 *       e este use case nem depende dessas portas.
 *   <li><strong>Auditoria terminal unica</strong>: {@code CREDORA_MATCHING_CONFIRMADA} ou {@code
 *       CREDORA_MATCHING_REJEITADA} apos o commit, uma vez por decisao.
 * </ul>
 */
@Service
public class DecidirMatchingCredoraOperacaoUseCase {

    private static final int MOTIVO_MAX = 255;

    private final MatchingCredoraOperacaoRepository matchingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DecidirMatchingCredoraOperacaoUseCase(
            MatchingCredoraOperacaoRepository matchingRepository, ApplicationEventPublisher eventPublisher) {
        this.matchingRepository = matchingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MatchingCredoraOperacaoView executar(DecidirMatchingCredoraOperacaoCommand cmd) {
        validarComando(cmd);

        MatchingCredoraOperacao matching =
                matchingRepository.findByIdForUpdate(cmd.sugestaoId()).orElseThrow(MatchingNaoEncontradoException::new);
        if (matching.getStatus().terminal()) {
            throw new MatchingDecisaoConflitanteException();
        }

        if (cmd.acao() == AcaoDecisaoMatching.CONFIRMAR) {
            matching.confirmar(cmd.atorId(), cmd.motivo());
            eventPublisher.publishEvent(new MatchingCredoraConfirmadoEvent(
                    matching.getId(),
                    matching.getOperacaoId(),
                    matching.getEmpresaCredoraId(),
                    matching.getMotivoDecisaoSanitizado(),
                    cmd.atorId()));
        } else {
            matching.rejeitar(cmd.atorId(), cmd.motivo());
            eventPublisher.publishEvent(new MatchingCredoraRejeitadoEvent(
                    matching.getId(),
                    matching.getOperacaoId(),
                    matching.getEmpresaCredoraId(),
                    matching.getMotivoDecisaoSanitizado(),
                    cmd.atorId()));
        }
        // Instancia managed (lida sob lock na mesma transacao): dirty checking persiste a decisao
        // no commit — mesmo idioma do ReconciliarAporteCredoraUseCase.
        return MatchingCredoraOperacaoView.de(matching);
    }

    private void validarComando(DecidirMatchingCredoraOperacaoCommand cmd) {
        if (cmd.sugestaoId() == null) {
            throw new ValidacaoException("CRD-400-011", "sugestaoId obrigatorio.");
        }
        if (cmd.acao() == null) {
            throw new ValidacaoException("CRD-400-012", "acao deve ser CONFIRMAR ou REJEITAR.");
        }
        if (cmd.motivo() != null && cmd.motivo().trim().length() > MOTIVO_MAX) {
            throw new ValidacaoException("CRD-400-013", "motivo nao pode exceder 255 caracteres.");
        }
        if (cmd.atorId() == null) {
            throw new ValidacaoException("CRD-400-014", "ator da decisao obrigatorio.");
        }
    }
}
