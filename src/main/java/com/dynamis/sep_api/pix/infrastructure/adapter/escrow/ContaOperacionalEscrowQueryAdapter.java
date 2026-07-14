package com.dynamis.sep_api.pix.infrastructure.adapter.escrow;

import com.dynamis.sep_api.escrow.domain.model.ContaEscrow;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.infrastructure.persistence.ContaEscrowRepository;
import com.dynamis.sep_api.pix.application.port.out.ContaOperacionalEscrowQueryPort;
import com.dynamis.sep_api.pix.application.port.out.dto.ContaOperacionalEscrowView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Adapter que resolve a conta operacional/escrow do SEP para o modulo {@code pix} (Sprint 31).
 * Fonte unica: a conta agregada de titular {@code "SEP-COBRANCA"} criada lazy pelo modulo
 * {@code escrow} desde a Sprint 12 ({@code RegistrarMovimentacaoEscrowUseCase.TITULAR_PADRAO});
 * operacional = status {@link StatusContaEscrow#ATIVA}.
 *
 * <p>O {@code contaTecnicaId} repassado ao provider usa o {@code externalId} quando existir (conta
 * real Celcoin, Fase 5); contas locais usam o proprio id — suficiente para o fake e para o skeleton
 * WireMock.
 */
@Component
public class ContaOperacionalEscrowQueryAdapter implements ContaOperacionalEscrowQueryPort {

    static final String TITULAR_CONTA_OPERACIONAL = "SEP-COBRANCA";

    private final ContaEscrowRepository contaEscrowRepository;

    public ContaOperacionalEscrowQueryAdapter(ContaEscrowRepository contaEscrowRepository) {
        this.contaEscrowRepository = contaEscrowRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContaOperacionalEscrowView> buscarContaOperacionalAtiva() {
        return contaEscrowRepository
                .findFirstByTitular(TITULAR_CONTA_OPERACIONAL)
                .filter(conta -> conta.getStatus() == StatusContaEscrow.ATIVA)
                .map(ContaOperacionalEscrowQueryAdapter::paraView);
    }

    private static ContaOperacionalEscrowView paraView(ContaEscrow conta) {
        String contaTecnicaId = conta.getExternalId() != null
                ? conta.getExternalId()
                : conta.getId().toString();
        return new ContaOperacionalEscrowView(conta.getId(), contaTecnicaId);
    }
}
