package com.dynamis.sep_api.cobranca.domain.event;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;

import java.util.UUID;

/**
 * Disparado quando {@code ExpirarRenegociacaoJob} marca renegociacao {@code EXPIRADA} apos a
 * janela de 7 dias sem decisao do tomador (Sprint 13 Task 13.6).
 *
 * <p>Separado de {@link RenegociacaoRecusadaEvent} pra preservar semantica: recusa eh ato
 * deliberado do tomador; expiracao eh inacao. Audit/backoffice (Sprint 14) precisa distinguir
 * pra metrica de engajamento.
 */
public record RenegociacaoExpiradaEvent(
        UUID renegociacaoId, UUID parcelaOriginalId, UUID tomadorId, StatusParcela statusParcelaRevertido) {}
