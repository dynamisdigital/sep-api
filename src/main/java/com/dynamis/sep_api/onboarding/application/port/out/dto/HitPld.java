package com.dynamis.sep_api.onboarding.application.port.out.dto;

import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;

import java.time.LocalDate;

/**
 * Hit individual em uma base PLD retornado pelo {@code BackgroundCheckProvider}. {@code motivo},
 * {@code severidade} e {@code dataInclusao} podem ser nulos quando a base nao expoe esses
 * detalhes. {@code payloadProvider} carrega o fragmento bruto da resposta — restrito a
 * persistencia/auditoria interna (LGPD).
 */
public record HitPld(
        BasePld base, String motivo, SeveridadePld severidade, LocalDate dataInclusao, String payloadProvider) {}
