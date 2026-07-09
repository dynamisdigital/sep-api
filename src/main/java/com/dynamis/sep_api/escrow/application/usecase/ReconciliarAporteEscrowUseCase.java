package com.dynamis.sep_api.escrow.application.usecase;

import com.dynamis.sep_api.escrow.domain.model.MovimentacaoEscrow;
import com.dynamis.sep_api.escrow.domain.vo.StatusMovimentacao;
import com.dynamis.sep_api.escrow.infrastructure.persistence.MovimentacaoEscrowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reconciliacao da movimentacao de aporte no escrow local (Sprint 29 Task 29.5). Liquidacao credita
 * o saldo da wallet (o registro do aporte NAO creditou — Task 29.2); falha nao credita. Ambas sao
 * idempotentes: replay do mesmo resultado terminal retorna a movimentacao sem novo efeito (em
 * especial, sem segundo credito). Somente movimentacoes de tipo {@code Aporte} sao aceitas.
 */
@Service
public class ReconciliarAporteEscrowUseCase {

    static final String TIPO_APORTE = "Aporte";

    private final MovimentacaoEscrowRepository movimentacaoRepository;

    public ReconciliarAporteEscrowUseCase(MovimentacaoEscrowRepository movimentacaoRepository) {
        this.movimentacaoRepository = movimentacaoRepository;
    }

    @Transactional
    public MovimentacaoEscrow liquidar(UUID movimentacaoId) {
        MovimentacaoEscrow movimentacao = obterAporte(movimentacaoId);
        if (movimentacao.getStatus() == StatusMovimentacao.LIQUIDADA) {
            return movimentacao;
        }
        movimentacao.marcarLiquidada();
        movimentacao.getWallet().creditar(movimentacao.getValor());
        return movimentacao;
    }

    @Transactional
    public MovimentacaoEscrow falhar(UUID movimentacaoId) {
        MovimentacaoEscrow movimentacao = obterAporte(movimentacaoId);
        if (movimentacao.getStatus() == StatusMovimentacao.FALHOU) {
            return movimentacao;
        }
        movimentacao.marcarFalhou();
        return movimentacao;
    }

    private MovimentacaoEscrow obterAporte(UUID movimentacaoId) {
        MovimentacaoEscrow movimentacao = movimentacaoRepository
                .findById(movimentacaoId)
                .orElseThrow(() -> new IllegalStateException("movimentacao de aporte inexistente"));
        if (!TIPO_APORTE.equals(movimentacao.getTipo())) {
            throw new IllegalStateException("movimentacao nao e de aporte");
        }
        return movimentacao;
    }
}
