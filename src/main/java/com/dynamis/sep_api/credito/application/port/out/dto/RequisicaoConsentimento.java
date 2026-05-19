package com.dynamis.sep_api.credito.application.port.out.dto;

import java.util.UUID;

/**
 * Requisicao para iniciar consentimento Open Finance via {@code OpenFinanceProvider}. Sprint 9.
 *
 * <p>Carrega apenas identificadores e callback URL — nenhum dado bancario do tomador, que e
 * coletado pelo proprio provedor apos o handoff via {@code urlAutorizacao}.
 */
public record RequisicaoConsentimento(UUID propostaId, UUID tomadorId, String cpfCnpjTomador, String redirectUri) {}
