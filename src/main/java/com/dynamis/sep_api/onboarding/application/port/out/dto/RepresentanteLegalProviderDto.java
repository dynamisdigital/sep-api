package com.dynamis.sep_api.onboarding.application.port.out.dto;

/**
 * Representante legal retornado pelo {@code KybProvider}. Contrato em termos de dominio — DTOs
 * Celcoin sao traduzidos pelo mapper.
 */
public record RepresentanteLegalProviderDto(String nome, String cpf, String cargo) {}
