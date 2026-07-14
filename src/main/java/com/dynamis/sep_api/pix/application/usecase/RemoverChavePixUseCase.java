package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.port.out.ContaOperacionalEscrowQueryPort;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ContaOperacionalEscrowView;
import com.dynamis.sep_api.pix.domain.event.PixChaveRemovidaEvent;
import com.dynamis.sep_api.pix.domain.model.ChavePix;
import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.infrastructure.persistence.ChavePixRepository;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Remocao assistida (logica) de chave Pix da conta operacional (Sprint 31 Task 31.6): transicao
 * {@code ATIVA -> INATIVA} — a linha nunca e apagada. Step-up estrito e roles ficam na borda REST
 * (Task 31.7).
 *
 * <p>Todo o fluxo roda em uma transacao com {@code SELECT FOR UPDATE} escopado na conta
 * operacional: remocoes concorrentes serializam — a segunda re-le {@code INATIVA} e encerra como
 * replay idempotente, sem segunda chamada ao provider nem auditoria duplicada. O provider e chamado
 * <strong>antes</strong> de persistir a transicao: falha externa reverte a transacao e a chave
 * permanece {@code ATIVA} para retentativa segura (remocao no provider e idempotente).
 *
 * <p>404 e neutro (mesma resposta para chave inexistente e conta operacional ausente), sem ecoar o
 * UUID consultado. Erros e eventos nunca carregam valor, hash, mascara ou provider id.
 */
@Service
public class RemoverChavePixUseCase {

    private final ChavePixRepository repository;
    private final ContaOperacionalEscrowQueryPort contaPort;
    private final PixProvider pixProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionTemplate transacao;

    public RemoverChavePixUseCase(
            ChavePixRepository repository,
            ContaOperacionalEscrowQueryPort contaPort,
            PixProvider pixProvider,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager txManager) {
        this.repository = repository;
        this.contaPort = contaPort;
        this.pixProvider = pixProvider;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.transacao = new TransactionTemplate(txManager);
    }

    public void executar(UUID chaveId, UUID operadorId, String correlationId) {
        ContaOperacionalEscrowView conta =
                contaPort.buscarContaOperacionalAtiva().orElseThrow(RemoverChavePixUseCase::naoEncontrada);

        transacao.executeWithoutResult(status -> {
            ChavePix chave = repository
                    .findByIdAndContaEscrowIdForUpdate(chaveId, conta.contaEscrowId())
                    .orElseThrow(RemoverChavePixUseCase::naoEncontrada);

            if (chave.getStatus() == StatusChavePix.INATIVA) {
                return; // replay idempotente: sem provider, sem persistencia, sem nova auditoria.
            }

            pixProvider.removerChave(chave.getProviderKeyId(), correlationId);

            chave.inativar(operadorId, OffsetDateTime.now(clock));
            repository.saveAndFlush(chave);
            eventPublisher.publishEvent(
                    new PixChaveRemovidaEvent(chave.getId(), chave.getContaEscrowId(), chave.getTipo(), operadorId));
        });
    }

    /** 404 neutro: nao distingue chave inexistente, fora do escopo da conta ou conta ausente. */
    private static RecursoNaoEncontradoException naoEncontrada() {
        return new RecursoNaoEncontradoException("PIX-404-CHAVE", "Chave Pix nao encontrada.");
    }
}
