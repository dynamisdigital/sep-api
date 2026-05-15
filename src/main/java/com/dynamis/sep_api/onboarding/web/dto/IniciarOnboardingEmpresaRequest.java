package com.dynamis.sep_api.onboarding.web.dto;

import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Request body para iniciar onboarding KYB PJ. */
@Schema(description = "Dados minimos para abrir solicitacao de onboarding KYB PJ")
public record IniciarOnboardingEmpresaRequest(
        @Schema(example = "27865757000102", description = "CNPJ com ou sem mascara") @NotBlank String cnpj,
        @Schema(example = "Acme Comercio LTDA") @NotBlank String razaoSocial,
        @Schema(example = "Acme") String nomeFantasia,
        @Schema(example = "LTDA") TipoSocietario tipoSocietario,
        @Schema(example = "ME") PorteEmpresa porte) {}
