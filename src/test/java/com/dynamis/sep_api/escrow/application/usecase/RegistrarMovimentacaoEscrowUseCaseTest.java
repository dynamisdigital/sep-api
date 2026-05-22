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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrarMovimentacaoEscrowUseCaseTest {

    private ContaEscrowRepository contaRepository;
    private WalletRepository walletRepository;
    private MovimentacaoEscrowRepository movimentacaoRepository;
    private RegistrarMovimentacaoEscrowUseCase useCase;

    @BeforeEach
    void setup() {
        contaRepository = mock(ContaEscrowRepository.class);
        walletRepository = mock(WalletRepository.class);
        movimentacaoRepository = mock(MovimentacaoEscrowRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        useCase = new RegistrarMovimentacaoEscrowUseCase(
                contaRepository, walletRepository, movimentacaoRepository, eventPublisher, txManager);
    }

    @Test
    void registrarRecebimento_criaContaWalletEMovimentacaoQuandoTudoNovo() {
        UUID propostaId = UUID.randomUUID();
        String key = "key-1";
        when(movimentacaoRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(walletRepository.findByPropostaIdForUpdate(propostaId)).thenReturn(Optional.empty());
        when(contaRepository.findFirstByTitular(RegistrarMovimentacaoEscrowUseCase.TITULAR_PADRAO))
                .thenReturn(Optional.empty());
        when(contaRepository.saveAndFlush(any(ContaEscrow.class))).thenAnswer(i -> i.getArgument(0));
        when(walletRepository.saveAndFlush(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));
        when(movimentacaoRepository.save(any(MovimentacaoEscrow.class))).thenAnswer(i -> i.getArgument(0));

        MovimentacaoEscrow mov = useCase.registrarRecebimento(novoCmd(propostaId, key, "100.00"));

        assertThat(mov.getValor()).isEqualByComparingTo("100.00");
        assertThat(mov.getIdempotencyKey()).isEqualTo(key);
        assertThat(mov.getWallet().getPropostaId()).isEqualTo(propostaId);
        assertThat(mov.getWallet().getSaldo()).isEqualByComparingTo("100.00");
        verify(contaRepository).saveAndFlush(any(ContaEscrow.class));
        verify(walletRepository).saveAndFlush(any(Wallet.class));
        verify(movimentacaoRepository).save(any(MovimentacaoEscrow.class));
    }

    @Test
    void registrarRecebimento_reutilizaWalletExistente() {
        UUID propostaId = UUID.randomUUID();
        String key = "key-2";
        ContaEscrow conta =
                ContaEscrow.criar(RegistrarMovimentacaoEscrowUseCase.TITULAR_PADRAO, StatusContaEscrow.ATIVA);
        Wallet wallet = Wallet.criar(conta, propostaId, TipoWallet.PROPOSTA);
        when(movimentacaoRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(walletRepository.findByPropostaIdForUpdate(propostaId)).thenReturn(Optional.of(wallet));
        when(movimentacaoRepository.save(any(MovimentacaoEscrow.class))).thenAnswer(i -> i.getArgument(0));

        useCase.registrarRecebimento(novoCmd(propostaId, key, "50.00"));

        verify(contaRepository, never()).saveAndFlush(any());
        verify(walletRepository, never()).saveAndFlush(any());
        assertThat(wallet.getSaldo()).isEqualByComparingTo("50.00");
    }

    @Test
    void registrarRecebimento_idempotente_retornaExistenteSemNovoSave() {
        String key = "key-existente";
        ContaEscrow conta =
                ContaEscrow.criar(RegistrarMovimentacaoEscrowUseCase.TITULAR_PADRAO, StatusContaEscrow.ATIVA);
        Wallet wallet = Wallet.criar(conta, UUID.randomUUID(), TipoWallet.PROPOSTA);
        MovimentacaoEscrow existente = MovimentacaoEscrow.criarRecebimento(
                wallet, new BigDecimal("100.00"), key, OffsetDateTime.now(), UUID.randomUUID());
        when(movimentacaoRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existente));

        MovimentacaoEscrow mov = useCase.registrarRecebimento(novoCmd(UUID.randomUUID(), key, "100.00"));

        assertThat(mov).isSameAs(existente);
        verify(walletRepository, never()).findByPropostaIdForUpdate(any());
        verify(movimentacaoRepository, never()).save(any());
    }

    private static RegistrarMovimentacaoEscrowCommand novoCmd(UUID propostaId, String key, String valor) {
        return new RegistrarMovimentacaoEscrowCommand(
                propostaId, new BigDecimal(valor), key, OffsetDateTime.now(), UUID.randomUUID());
    }
}
