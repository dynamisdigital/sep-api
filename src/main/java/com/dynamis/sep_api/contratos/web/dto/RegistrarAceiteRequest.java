package com.dynamis.sep_api.contratos.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aceite e por verbo HTTP — sem campos. Mantido como record (em vez de void body) para permitir
 * evolucao (ex.: declaracao de leitura, confirmacao explicita de termos) sem breaking change.
 */
@Schema(description = "Confirmacao de aceite (corpo vazio nesta versao)")
public record RegistrarAceiteRequest() {}
