package com.dynamis.sep_api.credores.infrastructure.adapter.escrow;

import com.dynamis.sep_api.credores.application.port.out.AporteEscrowRegistrado;
import com.dynamis.sep_api.credores.application.port.out.RegistrarAporteEscrowCommand;
import com.dynamis.sep_api.credores.application.port.out.RegistrarAporteEscrowPort;
import com.dynamis.sep_api.credores.domain.exception.AporteEscrowException;
import com.dynamis.sep_api.escrow.application.dto.RegistrarMovimentacaoEscrowCommand;
import com.dynamis.sep_api.escrow.application.usecase.RegistrarMovimentacaoEscrowUseCase;
import com.dynamis.sep_api.escrow.domain.model.MovimentacaoEscrow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Adapter do registro de aporte no escrow (Sprint 29 Task 29.2). Delega ao componente local {@link
 * RegistrarMovimentacaoEscrowUseCase} (escrow fake/local — nenhum dinheiro real; Celcoin/BaaS real
 * fica para a Fase 5; o port {@code EscrowProvider} nao e tocado). A chave de idempotencia no
 * escrow e namespaced como {@code aporte:<aporteId>} para nao colidir com o UNIQUE global de
 * {@code movimentacao_escrow} e caber no limite de 100 chars. Erro bruto vira {@link
 * AporteEscrowException} sanitizada; o original fica apenas em log.
 */
@Component
public class AporteEscrowAdapter implements RegistrarAporteEscrowPort {

    static final String PREFIXO_IDEMPOTENCIA_ESCROW = "aporte:";

    private static final Logger log = LoggerFactory.getLogger(AporteEscrowAdapter.class);

    private final RegistrarMovimentacaoEscrowUseCase escrowUseCase;
    private final Clock clock;

    public AporteEscrowAdapter(RegistrarMovimentacaoEscrowUseCase escrowUseCase, Clock clock) {
        this.escrowUseCase = escrowUseCase;
        this.clock = clock;
    }

    @Override
    public AporteEscrowRegistrado registrar(RegistrarAporteEscrowCommand comando) {
        try {
            MovimentacaoEscrow movimentacao = escrowUseCase.registrarAporte(new RegistrarMovimentacaoEscrowCommand(
                    comando.propostaId(),
                    comando.valor(),
                    PREFIXO_IDEMPOTENCIA_ESCROW + comando.aporteId(),
                    OffsetDateTime.now(clock),
                    comando.aporteId()));
            return new AporteEscrowRegistrado(
                    movimentacao.getId().toString(), movimentacao.getStatus().name());
        } catch (RuntimeException ex) {
            log.error("Falha ao registrar aporte {} no escrow", comando.aporteId(), ex);
            throw new AporteEscrowException(ex);
        }
    }
}
