package com.dynamis.sep_api.onboarding.web.dto;

import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Visao completa do status PJ — usada no GET. */
@Schema(description = "Status atual PJ + dados KYB permitidos + documentos + representantes + resultado")
public record StatusOnboardingEmpresaResponse(
        UUID id,
        StatusOnboarding status,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataModificacao,
        DadosEmpresa dadosEmpresa,
        List<DocumentoEnviadoResponse> documentosEnviados,
        List<RepresentanteLegalResponse> representantes,
        @Schema(description = "Presente apenas quando ja existe resultado do KybProvider")
                ResultadoResponse resultado) {

    @Schema(description = "Dados cadastrais da empresa permitidos para exposicao publica")
    public record DadosEmpresa(
            @Schema(example = "27.865.757/0001-02") String cnpj,
            @Schema(example = "Acme Comercio LTDA") String razaoSocial,
            @Schema(example = "Acme") String nomeFantasia,
            TipoSocietario tipoSocietario,
            PorteEmpresa porte) {}

    @Schema(description = "Resultado final do KybProvider")
    public record ResultadoResponse(
            StatusOnboarding statusFinal,
            @Schema(example = "situacao cadastral diferente de ATIVA") String motivo,
            OffsetDateTime dataResultado) {}
}
