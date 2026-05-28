package com.dynamis.sep_api.credores.application.dto;

import com.dynamis.sep_api.credores.domain.vo.TipoCredora;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de cadastro de empresa credora. {@code cnpj} e {@code razaoSocial} nao entram aqui:
 * derivam do onboarding PJ referenciado por {@code onboardingId} (fonte autoritativa).
 *
 * @param usuarioId usuario autenticado dono do cadastro
 * @param onboardingId solicitacao de onboarding PJ aprovada a ser vinculada
 * @param tipoCredora natureza operacional declarada
 * @param capacidadeAporte teto de aporte declarado; opcional
 */
public record CadastrarEmpresaCredoraCommand(
        UUID usuarioId, UUID onboardingId, TipoCredora tipoCredora, BigDecimal capacidadeAporte) {}
