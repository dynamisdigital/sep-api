package com.dynamis.sep_api.contratos.domain.vo;

/**
 * Tipo do contrato gerado pela formalizacao (Sprint 10).
 *
 * <ul>
 *   <li>{@code MUTUO} - contrato de mutuo padrao desta sprint (template completo).
 *   <li>{@code CCB} - Cedula de Credito Bancario; esqueleto nesta sprint, completa na Sprint 11.
 *   <li>{@code OUTROS} - placeholder para tipos futuros.
 * </ul>
 */
public enum TipoContrato {
    MUTUO,
    CCB,
    OUTROS;

    public String templatePath() {
        return switch (this) {
            case MUTUO -> "contratos/mutuo";
            case CCB -> "contratos/ccb";
            case OUTROS -> "contratos/mutuo";
        };
    }
}
