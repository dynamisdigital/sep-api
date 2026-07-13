package com.dynamis.sep_api.credores.domain.vo;

/**
 * Criterio de elegibilidade avaliado na geracao de sugestoes de matching credora-operacao (Sprint
 * 30). Os criterios atendidos no momento da sugestao compoem o {@code criteriosSnapshot} persistido
 * — auditavel sem recalculo futuro e sem dado sensivel (apenas codigos funcionais).
 *
 * <p>{@code CAPACIDADE_COMPORTA_VALOR} so participa quando a credora declarou
 * {@code capacidadeAporte} no perfil; capacidade ausente nao bloqueia o matching (criterio nao
 * aplicado, fora do snapshot).
 */
public enum CriterioMatchingCredoraOperacao {
    CREDORA_ATIVA,
    CREDORA_ELEGIVEL,
    OPERACAO_ATIVA,
    CONTRATO_ASSINADO,
    VALOR_OPERACAO_DISPONIVEL,
    CAPACIDADE_COMPORTA_VALOR,
    PAR_SEM_MATCHING_PREVIO
}
