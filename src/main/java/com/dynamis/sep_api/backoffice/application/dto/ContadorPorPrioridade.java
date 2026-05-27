package com.dynamis.sep_api.backoffice.application.dto;

import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;

public record ContadorPorPrioridade(PrioridadeItem prioridade, long total) {}
