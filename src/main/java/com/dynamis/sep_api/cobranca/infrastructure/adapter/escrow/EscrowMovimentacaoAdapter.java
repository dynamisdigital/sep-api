package com.dynamis.sep_api.cobranca.infrastructure.adapter.escrow;

import com.dynamis.sep_api.cobranca.application.port.out.RegistrarMovimentacaoEscrowPort;
import com.dynamis.sep_api.escrow.application.dto.RegistrarMovimentacaoEscrowCommand;
import com.dynamis.sep_api.escrow.application.usecase.RegistrarMovimentacaoEscrowUseCase;
import com.dynamis.sep_api.escrow.domain.model.MovimentacaoEscrow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Adapter que liga {@link RegistrarMovimentacaoEscrowPort} ao use case publico do modulo escrow.
 * Mantem cobranca livre de qualquer DTO/entidade do escrow (ADR 0007).
 */
@Component
public class EscrowMovimentacaoAdapter implements RegistrarMovimentacaoEscrowPort {

    private final RegistrarMovimentacaoEscrowUseCase useCase;

    public EscrowMovimentacaoAdapter(RegistrarMovimentacaoEscrowUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public MovimentacaoEscrowResult registrarRecebimento(
            UUID propostaId,
            BigDecimal valor,
            String idempotencyKey,
            OffsetDateTime dataMovimentacao,
            UUID externalReferenceId) {
        MovimentacaoEscrow mov = useCase.registrarRecebimento(new RegistrarMovimentacaoEscrowCommand(
                propostaId, valor, idempotencyKey, dataMovimentacao, externalReferenceId));
        return new MovimentacaoEscrowResult(mov.getId(), mov.getWallet().getId(), mov.getValor());
    }
}
