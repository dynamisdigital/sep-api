package com.dynamis.sep_api.credito.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body do {@code POST /api/v1/credito/propostas/{id}/open-finance/consentimento} (Sprint 9 Task
 * 9.6). Tomador autenticado fornece CPF/CNPJ e redirect URL pra o handoff de autorizacao.
 *
 * <p>Sprint 9 fix code review Task 9.6:
 *
 * <ul>
 *   <li>{@code cpfCnpjTomador} restrito a 11 ou 14 digitos puros (mitiga injecao + facilita
 *       masking em logs LGPD downstream);
 *   <li>{@code redirectUri} restrito a {@code http(s)://} (bloqueia {@code javascript:},
 *       {@code data:} e outros vetores SSRF/open redirect).
 * </ul>
 */
@Schema(description = "Requisicao pra iniciar consentimento Open Finance numa proposta")
public record IniciarConsentimentoOpenFinanceRequest(
        @Schema(description = "CPF (PF) com 11 digitos ou CNPJ (PJ) com 14, somente numeros", example = "52998224725")
                @NotBlank
                @Pattern(regexp = "\\d{11}|\\d{14}", message = "cpfCnpjTomador deve ter 11 ou 14 digitos")
                String cpfCnpjTomador,
        @Schema(
                        description =
                                "URL HTTPS pra qual o tomador sera redirecionado apos autorizar/negar no provider; deve ser controlada pelo cliente SEP",
                        example = "https://app.sep/auth/callback")
                @NotBlank
                @Pattern(
                        regexp = "^https?://[^\\s]+$",
                        message =
                                "redirectUri deve ser http(s) — outros schemes (javascript, data, etc.) sao rejeitados")
                String redirectUri) {}
