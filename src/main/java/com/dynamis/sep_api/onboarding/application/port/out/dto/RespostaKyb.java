package com.dynamis.sep_api.onboarding.application.port.out.dto;

import com.dynamis.sep_api.onboarding.domain.vo.SituacaoCadastral;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resposta consolidada de uma consulta KYB. Sempre carrega {@link #payloadProvider} bruto pra
 * trilha auditavel; demais campos podem ser nulos quando o provider nao retornou (ex.: situacao
 * {@code DESCONHECIDA}).
 */
public record RespostaKyb(
        SituacaoCadastral situacaoCadastral,
        String razaoSocial,
        String nomeFantasia,
        String cnaePrincipal,
        String cnaesSecundarios,
        BigDecimal capitalSocial,
        LocalDate dataAbertura,
        List<RepresentanteLegalProviderDto> representantes,
        String payloadProvider) {}
