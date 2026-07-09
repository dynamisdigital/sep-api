package com.dynamis.sep_api.credores.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Corpo do registro assistido de aporte (Sprint 29 Task 29.4). Payload minimo: somente {@code
 * valor} — a {@code Idempotency-Key} vem de header e a operacao vem do path.
 */
public record RegistrarAporteRequest(@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal valor) {}
