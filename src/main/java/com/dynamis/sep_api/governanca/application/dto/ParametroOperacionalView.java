package com.dynamis.sep_api.governanca.application.dto;

import com.dynamis.sep_api.governanca.domain.model.ParametroOperacional;
import com.dynamis.sep_api.governanca.domain.vo.TipoParametroOperacional;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Projecao read-only de um parametro operacional. */
public record ParametroOperacionalView(
        UUID id,
        String chave,
        TipoParametroOperacional tipo,
        String valor,
        String descricao,
        boolean ativo,
        int versao,
        OffsetDateTime dataModificacao) {

    public static ParametroOperacionalView de(ParametroOperacional p) {
        return new ParametroOperacionalView(
                p.getId(),
                p.getChave(),
                p.getTipo(),
                p.getValor(),
                p.getDescricao(),
                p.isAtivo(),
                p.getVersao(),
                p.getDataModificacao());
    }
}
