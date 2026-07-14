package com.dynamis.sep_api.pix.domain.vo;

/**
 * Tipos de chave Pix suportados pela gestao assistida (Sprint 31), alinhados ao DICT. A validacao e
 * a normalizacao por tipo ficam no {@code NormalizadorChavePix} (application).
 */
public enum TipoChavePix {
    CPF,
    CNPJ,
    EMAIL,
    TELEFONE,
    EVP
}
