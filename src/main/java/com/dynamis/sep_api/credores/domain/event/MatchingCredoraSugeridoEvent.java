package com.dynamis.sep_api.credores.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sugestao de matching credora-operacao gerada pelo refresh assistido (Sprint 30). {@code
 * usuarioId} e o operador financeiro/admin que disparou o refresh. Carrega apenas identificadores
 * tecnicos e valor — sem PII, criterios ou dados de contrato.
 */
public record MatchingCredoraSugeridoEvent(
        UUID matchingId, UUID operacaoId, UUID empresaCredoraId, BigDecimal valorElegivel, UUID usuarioId) {}
