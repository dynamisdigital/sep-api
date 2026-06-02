package com.dynamis.sep_api.pix.application.port.out.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projecao read-only de uma parcela de cobranca para geracao de referencia Pix de recebimento
 * (Sprint 21 Task 21.2). O modulo {@code pix} le apenas os identificadores e o valor em aberto
 * calculado por {@code cobranca} — nunca importa entidades/repositorios de cobranca.
 *
 * @param valorEmAberto valor devido atualizado em aberto, calculado por {@code cobranca} (inclui
 *     mora/multa quando ha atraso); o {@code pix} nao recalcula.
 * @param permiteRecebimento status da parcela permite recebimento (PENDENTE/PARCIALMENTE_PAGA/ATRASADA).
 */
public record ParcelaRecebimentoPixView(
        UUID parcelaId, UUID contratoId, UUID tomadorId, BigDecimal valorEmAberto, boolean permiteRecebimento) {}
