package com.dynamis.sep_api.cobranca.domain.vo;

/**
 * Tipos de {@code EventoCobranca} persistidos como historico do workflow de inadimplencia (Sprint
 * 13 Task 13.2). Diferentes de {@code TipoEventoSeguranca} (audit log de seguranca) — aqui o foco
 * eh trilha operacional da cobranca.
 */
public enum TipoEventoCobranca {
    /** Etapa automatica do workflow disparou envio (Task 13.4). */
    NOTIFICACAO_AUTOMATICA,
    /** Financeiro registra contato manual com tomador (Task 13.7). */
    CONTATO_MANUAL,
    /** Renegociacao proposta pelo financeiro (Task 13.6). */
    RENEGOCIACAO_PROPOSTA,
    /** Tomador aceitou a renegociacao (Task 13.6). */
    RENEGOCIACAO_ACEITA,
    /** Tomador recusou a renegociacao (Task 13.6). */
    RENEGOCIACAO_RECUSADA,
    /** Proposta nao decidida no prazo expirou (job — Task 13.6). */
    RENEGOCIACAO_EXPIRADA,
    /** Parcela atingiu 90+ dias de atraso (job — Task 13.5). */
    PARCELA_INADIMPLENTE
}
