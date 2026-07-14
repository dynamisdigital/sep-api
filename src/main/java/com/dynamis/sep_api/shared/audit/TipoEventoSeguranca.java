package com.dynamis.sep_api.shared.audit;

/**
 * Tipos de eventos persistidos em {@code audit_log_seguranca} (Sprint 5 Task 5.7).
 *
 * <p>Mantido como enum simples (nao sealed) para casar com o check constraint da migration V4 e
 * permitir uso em consultas JPQL.
 *
 * <p>Sprint 6 Task 6.1: adicionados 6 eventos {@code KYC_*} para a trilha auditavel reforcada do
 * modulo {@code onboarding}. Check constraint atualizado em V7.
 *
 * <p>Sprint 7 Task 7.8: adicionados 7 eventos {@code KYB_*} e {@code PLD_*} para a trilha
 * regulatoria PJ + Prevencao a Lavagem de Dinheiro (Resolucao CMN 4.656/2018). Check constraint
 * atualizado em V13.
 *
 * <p>Sprint 8 Task 8.4: adicionado {@code ROLE_ALTERADO} para auditar promocao/rebaixamento de
 * roles por ADMIN. Check constraint atualizado em V15.
 *
 * <p>Sprint 8 Task 8.6: adicionados 5 eventos {@code PROPOSTA_*}/{@code PARECER_*} para a trilha
 * auditavel reforcada da analise de credito (CMN 4.656/2018 Art. 9). Check constraint atualizado
 * em V16.
 *
 * <p>Sprint 9 Task 9.7: adicionados 5 eventos {@code OPEN_FINANCE_*} para o ciclo de consentimento
 * Open Finance Brasil + reavaliacao do score. Check constraint atualizado em V19.
 *
 * <p>Sprint 10 Task 10.7: adicionados 4 eventos {@code CONTRATO_*} para a trilha auditavel
 * reforcada da formalizacao contratual (CMN 4.656/2018 Art. 11 + LGPD com retencao minima de 10
 * anos). Check constraint atualizado em V22.
 *
 * <p>Sprint 11 Task 11.8: adicionados 6 eventos do ciclo de assinatura digital ({@code CCB_GERADA},
 * {@code ASSINATURA_ENVIADA}/{@code _VISUALIZADA}/{@code _ASSINADA}/{@code _RECUSADA},
 * {@code DOCUMENTO_ASSINADO_BAIXADO}). Mesma exigencia regulatoria (CMN 4.656/2018 + Lei
 * 10.931/2004 CCB + LGPD); {@code DOCUMENTO_ASSINADO_BAIXADO} grava ip+user-agent pra trilha de
 * acesso ao documento legal. Check constraint atualizado em V24.
 *
 * <p>Sprint 12 Task 12.7: adicionados 6 eventos do ciclo de cobranca ({@code AGENDA_GERADA},
 * {@code PARCELA_CRIADA}, {@code RECEBIMENTO_REGISTRADO}, {@code PARCELA_PAGA},
 * {@code PARCELA_ATRASADA}, {@code MOVIMENTACAO_ESCROW_CRIADA}). Exigencia regulatoria: CMN
 * 4.656/2018 Art. 9 + Art. 11 + segregacao patrimonial. Check constraint atualizado em V29.
 */
public enum TipoEventoSeguranca {
    LOGIN_OK,
    LOGIN_FAIL,
    TOTP_OK,
    TOTP_FAIL,
    BACKUP_CODE_USED,
    LOCKOUT,
    PASSWORD_CHANGED,
    MFA_ENABLED,
    MFA_DISABLED,
    REFRESH_REUSE_DETECTED,
    STEP_UP_OK,
    STEP_UP_FAIL,
    KYC_INICIADO,
    KYC_DOCUMENTO_ENVIADO,
    KYC_VERIFICACAO_DISPARADA,
    KYC_FINALIZADO_APROVADO,
    KYC_FINALIZADO_REPROVADO,
    KYC_FINALIZADO_PENDENCIA,
    KYB_INICIADO,
    KYB_FINALIZADO_APROVADO,
    KYB_FINALIZADO_REPROVADO,
    PLD_INICIADO,
    PLD_HIT_DETECTADO,
    PLD_LIMPO,
    PLD_FINALIZADO,
    ROLE_ALTERADO,
    PROPOSTA_CRIADA,
    PROPOSTA_AVALIADA_MOTOR,
    PARECER_REGISTRADO,
    PROPOSTA_APROVADA,
    PROPOSTA_REJEITADA,
    OPEN_FINANCE_CONSENTIMENTO_INICIADO,
    OPEN_FINANCE_AUTORIZADO,
    OPEN_FINANCE_NEGADO,
    OPEN_FINANCE_DADOS_RECEBIDOS,
    OPEN_FINANCE_REAVALIACAO,
    /** Sprint 15 — 15F-019: consentimento AUTORIZADO revogado tardiamente pelo detentor. */
    OPEN_FINANCE_REVOGADO,
    CONTRATO_GERADO,
    CONTRATO_NOVA_VERSAO,
    CONTRATO_ACEITO,
    CONTRATO_CANCELADO,
    CCB_GERADA,
    ASSINATURA_ENVIADA,
    ASSINATURA_VISUALIZADA,
    ASSINATURA_ASSINADA,
    ASSINATURA_RECUSADA,
    DOCUMENTO_ASSINADO_BAIXADO,
    AGENDA_GERADA,
    PARCELA_CRIADA,
    RECEBIMENTO_REGISTRADO,
    PARCELA_PAGA,
    PARCELA_ATRASADA,
    MOVIMENTACAO_ESCROW_CRIADA,
    // Sprint 13 Task 13.8: inadimplencia + renegociacao.
    NOTIFICACAO_ENVIADA,
    EVENTO_COBRANCA_REGISTRADO,
    PARCELA_INADIMPLENTE,
    RENEGOCIACAO_PROPOSTA,
    RENEGOCIACAO_ACEITA,
    RENEGOCIACAO_RECUSADA,
    RENEGOCIACAO_EXPIRADA,
    // Sprint 14 Task 14.8: operacoes do backoffice.
    ITEM_FILA_CRIADO,
    ITEM_ASSUMIDO,
    COMENTARIO_REGISTRADO,
    ITEM_RESOLVIDO,
    ITEM_IGNORADO,
    REPROCESSO_DISPARADO,
    // Sprint 16 Task 16.6: jornada da empresa credora.
    CREDORA_CADASTRADA,
    CREDORA_ELEGIVEL,
    CREDORA_INELEGIVEL,
    // Sprint 17 Task 17.6: oportunidades e carteira da credora.
    CREDORA_INTERESSE_REGISTRADO,
    CREDORA_INTERESSE_CANCELADO,
    CREDORA_OPERACAO_ASSOCIADA,
    // Sprint 29: aporte assistido da credora (Epic 15).
    CREDORA_APORTE_REGISTRADO,
    CREDORA_APORTE_LIQUIDADO,
    CREDORA_APORTE_FALHOU,
    // Sprint 30 Task 30.2: matching assistido credora-operacao (Epic 15).
    CREDORA_MATCHING_SUGERIDA,
    CREDORA_MATCHING_CONFIRMADA,
    CREDORA_MATCHING_REJEITADA,
    // Sprint 18 Task 18.6: governanca avancada (RBAC cumulativo + parametros).
    USUARIO_ROLES_ALTERADAS,
    PARAMETRO_OPERACIONAL_ALTERADO,
    // Sprint 19 Task 19.6: webhook Pix (foundation Epic 15).
    PIX_WEBHOOK_RECEBIDO,
    PIX_WEBHOOK_PROCESSADO,
    PIX_WEBHOOK_FALHOU,
    PIX_TRANSFERENCIA_SOLICITADA,
    PIX_TRANSFERENCIA_CONCLUIDA,
    PIX_TRANSFERENCIA_FALHOU,
    // Sprint 31 Task 31.5: gestao assistida de chaves Pix da conta operacional (Epic 15).
    PIX_CHAVE_CADASTRADA,
    PIX_CHAVE_REMOVIDA
}
