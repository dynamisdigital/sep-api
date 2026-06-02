package com.dynamis.sep_api.pix.application.port.out;

import com.dynamis.sep_api.pix.application.port.out.dto.RecebimentoPixCobrancaResult;
import com.dynamis.sep_api.pix.application.port.out.dto.RegistrarRecebimentoPixCobrancaCommand;

/**
 * Porta de saida para baixar uma parcela a partir de um recebimento Pix conciliado (Sprint 21 Task
 * 21.4). Implementada por adapter em {@code pix.infrastructure.adapter.cobranca}, que traduz para o
 * {@code RegistrarRecebimentoUseCase} de {@code cobranca} — dono unico do lock da parcela, do calculo
 * do valor devido, do status {@code PAGA}/{@code PARCIALMENTE_PAGA}, da criacao do {@code Recebimento}
 * e da movimentacao escrow. O dominio {@code pix} nunca toca entidades/repositorios de cobranca.
 */
public interface CobrancaRecebimentoPixPort {

    RecebimentoPixCobrancaResult registrarRecebimento(RegistrarRecebimentoPixCobrancaCommand comando);
}
