package com.dynamis.sep_api.pix.application.port.out.dto;

import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;

/**
 * Comando de entrada para {@code PixProvider#cadastrarChave} (Sprint 31). Linguagem de dominio SEP
 * — o adapter traduz para o formato do provider.
 *
 * <p>{@code valorNormalizado} e o unico lugar onde a chave em claro trafega (em memoria, ate a
 * chamada ao provider); nunca e persistido, logado ou incluido em erro. {@code contaTecnicaId} e o
 * identificador tecnico da conta operacional que o provider precisa — nunca dados bancarios.
 */
public record ComandoCadastrarChavePix(TipoChavePix tipo, String valorNormalizado, String contaTecnicaId) {

    /** Nao expoe o valor da chave em log/debug — apenas tipo e conta tecnica. */
    @Override
    public String toString() {
        return "ComandoCadastrarChavePix[tipo=" + tipo + ", contaTecnicaId=" + contaTecnicaId + "]";
    }
}
