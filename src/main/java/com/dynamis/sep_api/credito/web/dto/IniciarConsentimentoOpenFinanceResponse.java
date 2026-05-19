package com.dynamis.sep_api.credito.web.dto;

import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resposta do {@code POST .../open-finance/consentimento} (Sprint 9 Task 9.6). Carrega URL de
 * autorizacao que o cliente SEP abre em browser/webview pro tomador autorizar no provider.
 */
@Schema(description = "Consentimento Open Finance iniciado — tomador deve abrir urlAutorizacao")
public record IniciarConsentimentoOpenFinanceResponse(
        UUID consentimentoId, StatusConsentimento status, String urlAutorizacao, OffsetDateTime dataExpiracao) {}
