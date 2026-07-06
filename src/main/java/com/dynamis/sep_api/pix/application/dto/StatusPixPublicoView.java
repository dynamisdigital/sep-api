package com.dynamis.sep_api.pix.application.dto;

import com.dynamis.sep_api.pix.domain.vo.StatusPixPublico;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Projecao publica do status de desembolso Pix de um contrato (Sprint 26), consumida por outros
 * modulos (ex.: {@code credores}, Gate P3) atraves de {@code ConsultarStatusPixPorContratoUseCase}.
 * Nao valida ownership: quem consome ja validou a posse do contrato/operacao.
 */
public record StatusPixPublicoView(StatusPixPublico status, BigDecimal valor, OffsetDateTime atualizadoEm) {}
