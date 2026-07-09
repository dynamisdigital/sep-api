package com.dynamis.sep_api.credores.application.port.out;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando de registro do aporte no escrow (Sprint 29 Task 29.2). {@code aporteId} e a chave de
 * deduplicacao e rastreabilidade dentro do escrow; {@code propostaId} resolve a wallet da operacao
 * financiada (wallet por proposta, padrao Sprint 12).
 */
public record RegistrarAporteEscrowCommand(UUID aporteId, UUID propostaId, BigDecimal valor) {

    public RegistrarAporteEscrowCommand {
        Objects.requireNonNull(aporteId, "aporteId obrigatorio");
        Objects.requireNonNull(propostaId, "propostaId obrigatoria");
        Objects.requireNonNull(valor, "valor obrigatorio");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("valor deve ser positivo");
        }
    }
}
