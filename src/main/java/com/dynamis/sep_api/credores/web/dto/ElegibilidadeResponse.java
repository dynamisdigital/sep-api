package com.dynamis.sep_api.credores.web.dto;

import com.dynamis.sep_api.credores.domain.vo.StatusCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;

/** Status de elegibilidade operacional da credora e o status cadastral correspondente. */
public record ElegibilidadeResponse(
        StatusCredora status, StatusElegibilidade elegibilidade, String motivoInelegibilidade) {}
