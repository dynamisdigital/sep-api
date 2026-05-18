package com.dynamis.sep_api.credito.application.service.regras;

import com.dynamis.sep_api.credito.application.service.CreditoMotorProperties;
import com.dynamis.sep_api.credito.application.service.RegraCredito;
import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Valor solicitado deve respeitar limite por perfil (PF/PJ). Configuravel em
 * {@code app.credito.motor.valor-maximo-pf} e {@code app.credito.motor.valor-maximo-pj}.
 */
@Component
public class RegraValorMaximo implements RegraCredito {

    public static final String NOME = "valor-maximo-por-perfil";

    private final CreditoMotorProperties properties;

    public RegraValorMaximo(CreditoMotorProperties properties) {
        this.properties = properties;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public RegraResultado avaliar(ContextoAvaliacaoCredito contexto) {
        BigDecimal valor = contexto.proposta().getValorSolicitado();
        BigDecimal limite = contexto.isPessoa() ? properties.valorMaximoPf() : properties.valorMaximoPj();
        if (valor.compareTo(limite) <= 0) {
            return RegraResultado.passou(NOME);
        }
        return RegraResultado.falhou(NOME, "Valor R$ " + valor + " acima do limite R$ " + limite);
    }
}
