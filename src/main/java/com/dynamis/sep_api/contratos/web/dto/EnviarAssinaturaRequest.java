package com.dynamis.sep_api.contratos.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body de {@code POST /api/v1/contratos/{id}/assinar} (Sprint 11 Task 11.7). Vazio nesta sprint
 * — reservado para evolucao (signatarios adicionais, escolha de provider, exigir ICP-Brasil).
 */
@Schema(description = "Body reservado para evolucao; nenhum campo obrigatorio hoje")
public record EnviarAssinaturaRequest() {}
