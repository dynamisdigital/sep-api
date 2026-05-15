package com.dynamis.sep_api.onboarding.application.dto;

import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.StatusPldRepresentante;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Visao composta PJ — agrega solicitacao + KYB + documentos + representantes + resultado. */
public record StatusOnboardingEmpresaView(
        UUID id,
        StatusOnboarding status,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataModificacao,
        DadosEmpresaView dadosEmpresa,
        List<DocumentoEnviado> documentosEnviados,
        List<RepresentanteView> representantes,
        ResultadoView resultado) {

    public record DadosEmpresaView(
            String cnpj, String razaoSocial, String nomeFantasia, TipoSocietario tipoSocietario, PorteEmpresa porte) {}

    public record DocumentoEnviado(UUID id, TipoDocumento tipo, OffsetDateTime dataEnvio, String sha256) {}

    public record RepresentanteView(
            UUID id,
            String nome,
            String cpf,
            String cargo,
            StatusPldRepresentante statusPld,
            OffsetDateTime dataConsultaPld) {}

    public record ResultadoView(StatusOnboarding statusFinal, String motivo, OffsetDateTime dataResultado) {}
}
