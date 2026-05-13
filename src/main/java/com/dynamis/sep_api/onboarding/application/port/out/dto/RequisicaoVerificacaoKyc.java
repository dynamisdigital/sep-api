package com.dynamis.sep_api.onboarding.application.port.out.dto;

import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Requisicao enviada ao {@link com.dynamis.sep_api.onboarding.application.port.out.KycProvider}
 * para disparar uma verificacao KYC PF.
 *
 * <p>Carrega apenas metadados dos documentos ({@link DocumentoMetadados}); binarios NUNCA passam
 * pelo log (LGPD).
 */
public record RequisicaoVerificacaoKyc(
        UUID solicitacaoId,
        UUID usuarioId,
        String cpf,
        String nomeCompleto,
        LocalDate dataNascimento,
        List<DocumentoMetadados> documentos) {

    public record DocumentoMetadados(TipoDocumento tipo, String sha256, long tamanhoBytes, String mimeType) {}
}
