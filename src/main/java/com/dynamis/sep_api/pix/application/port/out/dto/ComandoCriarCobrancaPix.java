package com.dynamis.sep_api.pix.application.port.out.dto;

import java.math.BigDecimal;

/**
 * Comando de entrada para {@code PixProvider#criarCobrancaRecebimento} (Sprint 21 Task 21.2).
 * Linguagem de dominio SEP — o adapter traduz para o formato do provider.
 *
 * <p>O {@code txid} eh o identificador deterministico controlado pelo SEP que correlaciona o
 * webhook {@code RECEBIMENTO_PIX} de volta a parcela. {@code descricao} eh um rotulo generico, sem
 * dado pessoal/bancario (minimizacao).
 */
public record ComandoCriarCobrancaPix(String txid, BigDecimal valor, String descricao) {}
