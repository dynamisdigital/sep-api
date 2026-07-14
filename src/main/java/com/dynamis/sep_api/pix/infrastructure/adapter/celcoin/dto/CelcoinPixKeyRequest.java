package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corpo da requisicao de cadastro de chave Pix para a Celcoin (Sprint 31). Formato externo —
 * contrato <strong>skeleton local da Fase 4</strong>, a validar contra a documentacao real na Fase
 * 5. {@code chave} so existe aqui (body HTTP em memoria), nunca persistida ou logada.
 */
public record CelcoinPixKeyRequest(
        @JsonProperty("key_type") String tipo,
        @JsonProperty("key") String chave,
        @JsonProperty("account") String conta) {

    /** Nao expoe o valor da chave em log/debug. */
    @Override
    public String toString() {
        return "CelcoinPixKeyRequest[tipo=" + tipo + ", conta=" + conta + "]";
    }
}
