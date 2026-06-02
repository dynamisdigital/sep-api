package com.dynamis.sep_api.pix.application.dto;

import java.util.UUID;

/**
 * Comando para gerar (ou reaproveitar) uma referencia Pix de recebimento de uma parcela (Sprint 21
 * Task 21.2). O valor esperado nao vem do chamador — eh resolvido em {@code cobranca} para evitar
 * recalculo divergente no {@code pix}.
 */
public record GerarReferenciaRecebimentoPixCommand(UUID parcelaId, String correlationId) {}
