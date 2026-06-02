package com.dynamis.sep_api.pix.application.port.out;

import com.dynamis.sep_api.pix.application.port.out.dto.ParcelaRecebimentoPixView;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida para ler uma parcela de cobranca elegivel a recebimento Pix (Sprint 21 Task 21.2).
 * Implementada por adapter em {@code pix.infrastructure.adapter.cobranca}, que traduz para os
 * repositorios/use cases de {@code cobranca} sem expor suas entidades ao dominio {@code pix}.
 */
public interface CobrancaRecebimentoPixQueryPort {

    Optional<ParcelaRecebimentoPixView> buscarParcelaParaReferenciaPix(UUID parcelaId);
}
