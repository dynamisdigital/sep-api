package com.dynamis.sep_api.pix.domain.event;

import java.util.UUID;

/**
 * Disparado quando um recebimento Pix nao concilia normalmente (Sprint 21 Task 21.5): referencia
 * desconhecida, referencia nao-ATIVA, {@code endToEndId} ausente, valor parcial/maior ou falha de
 * baixa. Consumido pelo {@code RecebimentoPixDivergenteListener} para gerar item na fila operacional
 * do backoffice — a divergencia nunca fica apenas em log.
 *
 * <p>{@code motivo} eh texto operacional curto, sem payload bruto nem chave Pix.
 */
public record PixRecebimentoDivergenteEvent(UUID recebimentoId, String motivo) {}
