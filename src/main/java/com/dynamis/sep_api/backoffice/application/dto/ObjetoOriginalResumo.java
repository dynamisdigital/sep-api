package com.dynamis.sep_api.backoffice.application.dto;

import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;

import java.util.UUID;

/**
 * Projecao do objeto de dominio referenciado pelo item da fila (Sprint 14 Task 14.3). Carrega
 * apenas dados resumidos suficientes pra orientar o operador; detalhe completo via API publica
 * do modulo dono. Strategies registradas no {@code ResolvedorObjetoOriginalDispatcher} preenchem
 * este record por tipo de entidade.
 */
public record ObjetoOriginalResumo(
        TipoEntidadeReferenciada tipoEntidade, UUID entidadeId, String status, String descricaoCurta) {}
