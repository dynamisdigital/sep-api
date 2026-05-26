package com.dynamis.sep_api.backoffice.application.dto;

import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;

public record ContadorPorTipo(TipoItemFila tipo, long total) {}
