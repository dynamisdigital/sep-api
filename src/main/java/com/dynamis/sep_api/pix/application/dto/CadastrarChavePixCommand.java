package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;

import java.util.UUID;

/**
 * Comando de cadastro assistido de chave Pix (Sprint 31). {@code valor} e a chave em claro recebida
 * na borda REST — existe apenas em memoria ate a chamada ao provider; nunca persistida, logada ou
 * ecoada em erro.
 */
public record CadastrarChavePixCommand(
        TipoChavePix tipo, String valor, String idempotencyKey, UUID operadorId, String correlationId) {

    /** Nao expoe o valor da chave em log/debug. */
    @Override
    public String toString() {
        return "CadastrarChavePixCommand[tipo=" + tipo + ", idempotencyKey=" + idempotencyKey + ", operadorId="
                + operadorId + "]";
    }
}
