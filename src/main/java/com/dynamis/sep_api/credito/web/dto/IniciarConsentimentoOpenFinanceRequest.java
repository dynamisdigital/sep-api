package com.dynamis.sep_api.credito.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Body do {@code POST /api/v1/credito/propostas/{id}/open-finance/consentimento} (Sprint 9 Task
 * 9.6). Tomador autenticado fornece CPF/CNPJ e redirect URL pra o handoff de autorizacao.
 */
@Schema(description = "Requisicao pra iniciar consentimento Open Finance numa proposta")
public record IniciarConsentimentoOpenFinanceRequest(
        @Schema(description = "CPF (PF) ou CNPJ (PJ) do tomador, somente digitos", example = "52998224725") @NotBlank
                String cpfCnpjTomador,
        @Schema(
                        description =
                                "URL pra qual o tomador sera redirecionado apos autorizar/negar no provider; deve ser controlada pelo cliente SEP",
                        example = "https://app.sep/auth/callback")
                @NotBlank
                String redirectUri) {}
