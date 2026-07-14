package com.dynamis.sep_api.pix.web.dto;

import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request de cadastro assistido de chave Pix da conta operacional (Sprint 31 Task 31.7). O valor em
 * claro existe apenas neste request e na chamada ao provider — nunca persistido, logado ou ecoado.
 */
@Schema(description = "Cadastro de chave Pix da conta operacional/escrow")
public record CadastrarChavePixRequest(
        @Schema(
                        description = "Tipo da chave (CPF, CNPJ, EMAIL, TELEFONE ou EVP)",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                TipoChavePix tipo,
        @Schema(
                        description = "Valor da chave; normalizado por tipo e nunca persistido ou retornado em claro.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String valor) {

    /** Nao expoe o valor da chave em log/debug. */
    @Override
    public String toString() {
        return "CadastrarChavePixRequest[tipo=" + tipo + "]";
    }
}
