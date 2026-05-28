package com.dynamis.sep_api.credores.application.dto;

import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.PerfilCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;
import com.dynamis.sep_api.credores.domain.vo.TipoCredora;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Projecao read-only de uma empresa credora e seu perfil operacional, retornada pelos use cases. */
public record EmpresaCredoraView(
        UUID id,
        UUID usuarioId,
        UUID onboardingId,
        String cnpj,
        String razaoSocial,
        StatusCredora status,
        StatusElegibilidade elegibilidade,
        String motivoInelegibilidade,
        TipoCredora tipoCredora,
        BigDecimal capacidadeAporte,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataModificacao) {

    public static EmpresaCredoraView de(EmpresaCredora empresa, PerfilCredora perfil) {
        return new EmpresaCredoraView(
                empresa.getId(),
                empresa.getUsuarioId(),
                empresa.getOnboardingId(),
                empresa.getCnpj(),
                empresa.getRazaoSocial(),
                empresa.getStatus(),
                empresa.getElegibilidade(),
                empresa.getMotivoInelegibilidade(),
                perfil.getTipoCredora(),
                perfil.getCapacidadeAporte(),
                empresa.getDataCriacao(),
                empresa.getDataModificacao());
    }
}
