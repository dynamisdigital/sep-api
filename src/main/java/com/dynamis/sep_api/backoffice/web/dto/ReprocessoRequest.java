package com.dynamis.sep_api.backoffice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Body opcional dos endpoints de reprocesso. Quando informado, o item da fila eh referenciado
 * pelo registro de {@code Reprocesso}.
 */
@Schema(description = "Body opcional do reprocesso — referencia item da fila se houver.")
public record ReprocessoRequest(@Schema(description = "ID do item da fila relacionado (opcional)") UUID itemId) {}
