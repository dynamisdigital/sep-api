package com.dynamis.sep_api.contratos.domain.vo;

import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

/**
 * Tipo de contrato nao tem template proprio modelado. HTTP 422. Atualmente lancado quando o tipo
 * e {@link TipoContrato#OUTROS} — placeholder reservado para tipos futuros que precisam de
 * template e validacao juridica especificos.
 */
public class TipoContratoSemTemplateException extends OperacaoNaoProcessavelException {

    public static final String CODIGO = "CTR-422-002";

    public TipoContratoSemTemplateException(TipoContrato tipo) {
        super(CODIGO, "Tipo de contrato '" + tipo + "' nao tem template proprio modelado");
    }
}
