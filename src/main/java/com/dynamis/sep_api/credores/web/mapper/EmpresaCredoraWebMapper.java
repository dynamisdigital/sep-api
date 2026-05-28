package com.dynamis.sep_api.credores.web.mapper;

import com.dynamis.sep_api.credores.application.dto.EmpresaCredoraView;
import com.dynamis.sep_api.credores.web.dto.ElegibilidadeResponse;
import com.dynamis.sep_api.credores.web.dto.EmpresaCredoraResponse;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import org.mapstruct.Mapper;

/** MapStruct: view da camada application -> DTOs web do modulo credores. */
@Mapper
public interface EmpresaCredoraWebMapper {

    default EmpresaCredoraResponse toResponse(EmpresaCredoraView view) {
        return new EmpresaCredoraResponse(
                view.id(),
                view.usuarioId(),
                view.onboardingId(),
                formatarCnpj(view.cnpj()),
                view.razaoSocial(),
                view.status(),
                view.elegibilidade(),
                view.motivoInelegibilidade(),
                view.tipoCredora(),
                view.capacidadeAporte(),
                view.dataCriacao(),
                view.dataModificacao());
    }

    default ElegibilidadeResponse toElegibilidadeResponse(EmpresaCredoraView view) {
        return new ElegibilidadeResponse(view.status(), view.elegibilidade(), view.motivoInelegibilidade());
    }

    static String formatarCnpj(String cnpjBruto) {
        if (cnpjBruto == null) return null;
        String digitos = cnpjBruto.replaceAll("\\D", "");
        if (digitos.length() != 14) return cnpjBruto;
        return new Cnpj(digitos).formatado();
    }
}
