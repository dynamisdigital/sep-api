package com.dynamis.sep_api.escrow.application.usecase;

import com.dynamis.sep_api.escrow.application.dto.RegistrarMovimentacaoEscrowCommand;
import com.dynamis.sep_api.escrow.domain.event.MovimentacaoEscrowCriadaEvent;
import com.dynamis.sep_api.escrow.domain.model.ContaEscrow;
import com.dynamis.sep_api.escrow.domain.model.MovimentacaoEscrow;
import com.dynamis.sep_api.escrow.domain.model.Wallet;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.domain.vo.TipoWallet;
import com.dynamis.sep_api.escrow.infrastructure.persistence.ContaEscrowRepository;
import com.dynamis.sep_api.escrow.infrastructure.persistence.MovimentacaoEscrowRepository;
import com.dynamis.sep_api.escrow.infrastructure.persistence.WalletRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Use case publico do modulo {@code escrow} (Sprint 12 Task 12.4) que materializa a segregacao
 * patrimonial obrigatoria por Resolucao CMN 4.656/2018 (ADR 0005).
 *
 * <p>Sprint 12 entrega a versao local — wallet/conta tecnica criada lazy sob titular fixo
 * {@code "SEP-COBRANCA"}. Epic 15 substituira a persistencia local por integracao Celcoin via
 * {@code EscrowProvider}.
 *
 * <p>Idempotencia: {@code idempotencyKey} unica em {@code movimentacao_escrow}. Chamadas
 * repetidas retornam a movimentacao existente sem creditar o saldo de novo.
 *
 * <p>Criacao lazy de {@code ContaEscrow}/{@code Wallet} usa {@link TransactionTemplate} com
 * {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW}: corridas concorrentes que violam
 * {@code UNIQUE} (V28) abortam apenas a sub-tx de create, permitindo que a tx principal continue
 * com {@code findBy*} pra recuperar o registro vencedor.
 */
@Service
public class RegistrarMovimentacaoEscrowUseCase {

    static final String TITULAR_PADRAO = "SEP-COBRANCA";

    private final ContaEscrowRepository contaRepository;
    private final WalletRepository walletRepository;
    private final MovimentacaoEscrowRepository movimentacaoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate txRequiresNew;

    public RegistrarMovimentacaoEscrowUseCase(
            ContaEscrowRepository contaRepository,
            WalletRepository walletRepository,
            MovimentacaoEscrowRepository movimentacaoRepository,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager txManager) {
        this.contaRepository = contaRepository;
        this.walletRepository = walletRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.eventPublisher = eventPublisher;
        this.txRequiresNew = new TransactionTemplate(txManager);
        this.txRequiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public MovimentacaoEscrow registrarRecebimento(RegistrarMovimentacaoEscrowCommand cmd) {
        return movimentacaoRepository
                .findByIdempotencyKey(cmd.idempotencyKey())
                .orElseGet(() -> criarNovaMovimentacao(cmd));
    }

    private MovimentacaoEscrow criarNovaMovimentacao(RegistrarMovimentacaoEscrowCommand cmd) {
        Wallet wallet = obterOuCriarWallet(cmd.propostaId());
        wallet.creditar(cmd.valor());
        MovimentacaoEscrow movimentacao = MovimentacaoEscrow.criarRecebimento(
                wallet, cmd.valor(), cmd.idempotencyKey(), cmd.dataMovimentacao(), cmd.externalReferenceId());
        MovimentacaoEscrow salva = movimentacaoRepository.save(movimentacao);
        eventPublisher.publishEvent(new MovimentacaoEscrowCriadaEvent(
                salva.getId(),
                wallet.getId(),
                cmd.propostaId(),
                salva.getValor(),
                salva.getTipo(),
                salva.getDataMovimentacao(),
                salva.getExternalReferenceId()));
        return salva;
    }

    private Wallet obterOuCriarWallet(UUID propostaId) {
        return walletRepository.findByPropostaIdForUpdate(propostaId).orElseGet(() -> tentarCriarWallet(propostaId));
    }

    private Wallet tentarCriarWallet(UUID propostaId) {
        ContaEscrow conta = obterOuCriarContaTecnica();
        try {
            return txRequiresNew.execute(
                    status -> walletRepository.saveAndFlush(Wallet.criar(conta, propostaId, TipoWallet.PROPOSTA)));
        } catch (DataIntegrityViolationException ex) {
            // Sub-tx de create abortou no UNIQUE PARCIAL wallet(proposta_id). A tx principal segue
            // viva; recuperamos a wallet vencedora aqui.
            return walletRepository
                    .findByPropostaIdForUpdate(propostaId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Corrida em wallet sem registro persistido para proposta " + propostaId, ex));
        }
    }

    private ContaEscrow obterOuCriarContaTecnica() {
        return contaRepository.findFirstByTitular(TITULAR_PADRAO).orElseGet(this::tentarCriarContaTecnica);
    }

    private ContaEscrow tentarCriarContaTecnica() {
        try {
            return txRequiresNew.execute(
                    status -> contaRepository.saveAndFlush(ContaEscrow.criar(TITULAR_PADRAO, StatusContaEscrow.ATIVA)));
        } catch (DataIntegrityViolationException ex) {
            // Sub-tx abortou no UNIQUE conta_escrow.titular (V28). Re-busca em tx principal viva.
            return contaRepository
                    .findFirstByTitular(TITULAR_PADRAO)
                    .orElseThrow(() -> new IllegalStateException(
                            "Corrida em conta_escrow sem registro persistido para titular " + TITULAR_PADRAO, ex));
        }
    }
}
