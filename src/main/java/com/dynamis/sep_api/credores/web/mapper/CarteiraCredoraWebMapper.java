package com.dynamis.sep_api.credores.web.mapper;

import com.dynamis.sep_api.credores.application.dto.InteresseView;
import com.dynamis.sep_api.credores.application.dto.OperacaoCarteiraView;
import com.dynamis.sep_api.credores.application.dto.OportunidadeView;
import com.dynamis.sep_api.credores.web.dto.InteresseResponse;
import com.dynamis.sep_api.credores.web.dto.OperacaoCarteiraResponse;
import com.dynamis.sep_api.credores.web.dto.OportunidadeResponse;
import org.mapstruct.Mapper;

/** MapStruct: views da camada application -> DTOs web da carteira credora (Sprint 17). */
@Mapper
public interface CarteiraCredoraWebMapper {

    OportunidadeResponse toResponse(OportunidadeView view);

    InteresseResponse toResponse(InteresseView view);

    OperacaoCarteiraResponse toResponse(OperacaoCarteiraView view);
}
