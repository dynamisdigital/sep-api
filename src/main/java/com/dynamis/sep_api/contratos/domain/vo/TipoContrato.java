package com.dynamis.sep_api.contratos.domain.vo;

/**
 * Tipo do contrato gerado pela formalizacao (Sprint 10).
 *
 * <ul>
 *   <li>{@code MUTUO} - contrato de mutuo padrao desta sprint (template completo).
 *   <li>{@code CCB} - Cedula de Credito Bancario; esqueleto nesta sprint, completa na Sprint 11.
 *   <li>{@code OUTROS} - placeholder para tipos futuros. NAO tem template proprio — geracao deve
 *       falhar ate que template especifico seja modelado, para evitar contrato juridicamente
 *       incorreto caindo silenciosamente no template de MUTUO.
 * </ul>
 */
public enum TipoContrato {
    MUTUO,
    CCB,
    OUTROS;

    /**
     * Caminho do template (sem prefix/suffix) para este tipo de contrato.
     *
     * @throws TipoContratoSemTemplateException se o tipo nao tem template proprio modelado.
     */
    public String templatePath() {
        return switch (this) {
            case MUTUO -> "contratos/mutuo";
            case CCB -> "contratos/ccb";
            case OUTROS -> throw new TipoContratoSemTemplateException(this);
        };
    }
}
