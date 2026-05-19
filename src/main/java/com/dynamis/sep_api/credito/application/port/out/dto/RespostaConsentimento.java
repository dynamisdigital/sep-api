package com.dynamis.sep_api.credito.application.port.out.dto;

import java.time.OffsetDateTime;

/**
 * Resposta sincrona do {@code OpenFinanceProvider#iniciarConsentimento}. URL de autorizacao deve
 * ser exposta ao tomador (web/mobile abre em browser/webview); id externo correlaciona callback.
 */
public record RespostaConsentimento(String idExterno, String urlAutorizacao, OffsetDateTime dataExpiracao) {}
