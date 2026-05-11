package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Dados de setup TOTP. Backup codes sao exibidos uma unica vez.")
public record TotpSetupResponseDto(
        @Schema(description = "Secret Base32. Exibido apenas no setup, nao persistido em claro.") String secretBase32,
        @Schema(description = "URI otpauth:// para configurar app autenticador (Google Authenticator, Authy).")
                String otpAuthUri,
        @Schema(description = "Data URL PNG do QR code (renderizar em <img src>).") String qrCodeDataUrl,
        @Schema(description = "10 backup codes claros. Exibidos uma unica vez.") List<String> backupCodes) {}
