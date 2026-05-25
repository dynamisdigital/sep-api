package com.dynamis.sep_api.cobranca.domain.event;

import com.dynamis.sep_api.cobranca.domain.vo.TipoEventoCobranca;

import java.util.UUID;

/**
 * Disparado apos persistir um {@code EventoCobranca} (Sprint 13). Consumido pela auditoria
 * reforcada (Task 13.8) e pelo backoffice (Sprint 14).
 */
public record EventoCobrancaRegistradoEvent(
        UUID eventoId, UUID parcelaId, TipoEventoCobranca tipo, Integer diasAtraso, UUID registradoPor) {}
