package com.dynamis.sep_api.backoffice.application.dto;

import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;

public record ContadorPorStatus(StatusItemFila status, long total) {}
