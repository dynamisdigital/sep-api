package com.dynamis.sep_api.contratos.application.port.out.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Requisicao de envio do PDF da CCB ao provider de assinatura digital (Sprint 11 Task 11.4).
 *
 * <p>{@code idempotencyKey} e responsabilidade do use case (Task 11.5): derivada de
 * {@code contratoId + numeroVersao} pra garantir que reenvio do mesmo contrato/versao retorne o
 * envelope existente.
 *
 * <p>{@code signatarioEmail} e {@code signatarioNome} podem ser placeholders quando onboarding
 * ainda nao expoe dados cadastrais completos (limitacao documentada em CCB.md/CONTRATOS.md). Em
 * producao real esses campos vem do modulo onboarding.
 */
public record RequisicaoEnvioAssinatura(
        UUID contratoId, UUID versaoId, String signatarioEmail, String signatarioNome, String idempotencyKey) {

    public RequisicaoEnvioAssinatura {
        Objects.requireNonNull(contratoId, "contratoId obrigatorio");
        Objects.requireNonNull(versaoId, "versaoId obrigatoria");
        Objects.requireNonNull(signatarioEmail, "signatarioEmail obrigatorio");
        Objects.requireNonNull(signatarioNome, "signatarioNome obrigatorio");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey obrigatorio");
        if (signatarioEmail.isBlank() || signatarioNome.isBlank() || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("campos obrigatorios nao podem ser em branco");
        }
    }
}
