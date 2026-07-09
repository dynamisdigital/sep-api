package com.dynamis.sep_api.credores.application.port.out;

/**
 * Porta de saida para registrar a movimentacao de aporte da credora no escrow (Sprint 29 Task
 * 29.2). Consumer-driven: expressa somente o que o registro assistido precisa; o adapter traduz
 * para o componente de escrow vigente (fake/local nesta fase — nenhum dinheiro real; Celcoin/BaaS
 * real fica para a Fase 5).
 */
public interface RegistrarAporteEscrowPort {

    /**
     * Registra o aporte no escrow de forma idempotente: o mesmo {@code aporteId} retorna sempre o
     * mesmo registro. Falha e propagada como {@link
     * com.dynamis.sep_api.credores.domain.exception.AporteEscrowException} com motivo sanitizado
     * (erro bruto do escrow/provider nunca chega ao chamador).
     */
    AporteEscrowRegistrado registrar(RegistrarAporteEscrowCommand comando);
}
