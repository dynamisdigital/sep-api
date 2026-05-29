package com.dynamis.sep_api.pix.application.port.out.dto;

import java.math.BigDecimal;

/**
 * Comando de entrada para {@code PixProvider#solicitarTransferencia}. Linguagem de dominio SEP — o
 * adapter traduz para o formato Celcoin.
 *
 * <p>{@code chavePixDestino} so trafega aqui como input do provider; nao e persistida no dominio
 * (minimizacao de dados sensiveis — {@code PixTransferencia} guarda apenas valor/descricao/ids).
 */
public record ComandoTransferenciaPix(BigDecimal valor, String chavePixDestino, String descricao) {}
