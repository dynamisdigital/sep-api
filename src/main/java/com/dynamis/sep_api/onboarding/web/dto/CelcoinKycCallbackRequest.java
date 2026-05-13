package com.dynamis.sep_api.onboarding.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload do webhook Celcoin KYC. Mantemos schema flexivel ({@code @JsonIgnoreProperties} via
 * config global) para acomodar campos futuros do provider sem quebrar o controller.
 *
 * @param idVerificacao ID retornado em {@code POST /verifications} — chave para localizar a
 *     solicitacao via {@code SolicitacaoOnboardingRepository.findByIdVerificacaoExterna}.
 * @param status valores: {@code APPROVED}, {@code REJECTED}, {@code PENDING}, {@code PROCESSING}.
 * @param reason motivo curto para rejected/pending; pode ser null.
 */
public record CelcoinKycCallbackRequest(
        @JsonProperty("verification_id") String idVerificacao,
        @JsonProperty("status") String status,
        @JsonProperty("reason") String reason) {}
