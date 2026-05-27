package com.dynamis.sep_api.credito.domain.event;

import java.util.UUID;

/**
 * Evento publicado quando consentimento Open Finance previamente AUTORIZADO e revogado pelo
 * detentor via app do banco — provider notifica NEGADO tardio (Sprint 15 — 15F-019).
 *
 * <p>Semantica distinta de {@link OpenFinanceNegadoEvent}: revogacao acontece apos uso legitimo do
 * consentimento, entao score/movimentacao ja consultados permanecem validos, mas novas consultas
 * devem ser bloqueadas. Consumido pelo audit listener pra gravar {@code OPEN_FINANCE_REVOGADO}.
 *
 * <p>{@code idExternoCelcoin} preserva rastreabilidade do consentimento externo revogado para a
 * trilha de auditoria — alinha payload com {@link OpenFinanceAutorizadoEvent}.
 */
public record OpenFinanceRevogadoEvent(UUID consentimentoId, UUID propostaId, UUID tomadorId, String idExternoCelcoin) {}
