package com.dynamis.sep_api.escrow.application.usecase;

import com.dynamis.sep_api.escrow.application.dto.RegistrarMovimentacaoEscrowCommand;
import com.dynamis.sep_api.escrow.domain.model.ContaEscrow;
import com.dynamis.sep_api.escrow.domain.model.MovimentacaoEscrow;
import com.dynamis.sep_api.escrow.domain.model.Wallet;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.domain.vo.TipoWallet;
import com.dynamis.sep_api.escrow.infrastructure.persistence.ContaEscrowRepository;
import com.dynamis.sep_api.escrow.infrastructure.persistence.MovimentacaoEscrowRepository;
import com.dynamis.sep_api.escrow.infrastructure.persistence.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
public class RegistrarMovimentacaoEscrowUseCase {

    static final String TITULAR_PADRAO = "SEP-COBRANCA";

    private final ContaEscrowRepository contaRepository;
    private final WalletRepository walletRepository;
    private final MovimentacaoEscrowRepository movimentacaoRepository;

    public RegistrarMovimentacaoEscrowUseCase(
            ContaEscrowRepository contaRepository,
            WalletRepository walletRepository,
            MovimentacaoEscrowRepository movimentacaoRepository) {
        this.contaRepository = contaRepository;
        this.walletRepository = walletRepository;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    @Transactional
    public MovimentacaoEscrow registrarRecebimento(RegistrarMovimentacaoEscrowCommand cmd) {
        return movimentacaoRepository
                .findByIdempotencyKey(cmd.idempotencyKey())
                .orElseGet(() -> criarNovaMovimentacao(cmd));
    }

    private MovimentacaoEscrow criarNovaMovimentacao(RegistrarMovimentacaoEscrowCommand cmd) {
        Wallet wallet = walletRepository
                .findByPropostaIdForUpdate(cmd.propostaId())
                .orElseGet(() -> criarWalletParaProposta(cmd.propostaId()));
        wallet.creditar(cmd.valor());
        MovimentacaoEscrow movimentacao = MovimentacaoEscrow.criarRecebimento(
                wallet, cmd.valor(), cmd.idempotencyKey(), cmd.dataMovimentacao(), cmd.externalReferenceId());
        return movimentacaoRepository.save(movimentacao);
    }

    private Wallet criarWalletParaProposta(java.util.UUID propostaId) {
        ContaEscrow conta = contaRepository
                .findFirstByTitular(TITULAR_PADRAO)
                .orElseGet(() -> contaRepository.save(ContaEscrow.criar(TITULAR_PADRAO, StatusContaEscrow.ATIVA)));
        return walletRepository.save(Wallet.criar(conta, propostaId, TipoWallet.PROPOSTA));
    }
}
