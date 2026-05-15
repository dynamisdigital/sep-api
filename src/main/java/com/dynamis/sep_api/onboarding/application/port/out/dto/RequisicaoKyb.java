package com.dynamis.sep_api.onboarding.application.port.out.dto;

import java.util.List;
import java.util.UUID;

/**
 * Requisicao enviada ao {@link com.dynamis.sep_api.onboarding.application.port.out.KybProvider}
 * para disparar uma verificacao KYB PJ.
 *
 * <p>Carrega apenas metadados dos documentos (binarios NUNCA passam pelo log/payload — LGPD).
 */
public record RequisicaoKyb(
        UUID solicitacaoId,
        UUID usuarioId,
        String cnpj,
        String razaoSocialInformada,
        List<DocumentoMetadadosKyb> documentos) {

    public record DocumentoMetadadosKyb(String tipo, String sha256, long tamanhoBytes, String mimeType) {}
}
