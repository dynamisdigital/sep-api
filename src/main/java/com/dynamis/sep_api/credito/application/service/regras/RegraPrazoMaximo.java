package com.dynamis.sep_api.credito.application.service.regras;

import com.dynamis.sep_api.credito.application.service.CreditoMotorProperties;
import com.dynamis.sep_api.credito.application.service.RegraCredito;
import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import org.springframework.stereotype.Component;

/**
 * Prazo solicitado em meses deve respeitar limite por perfil. Configuravel em
 * {@code app.credito.motor.prazo-maximo-pf-meses} e {@code app.credito.motor.prazo-maximo-pj-meses}.
 */
@Component
public class RegraPrazoMaximo implements RegraCredito {

    public static final String NOME = "prazo-maximo-por-perfil";

    private final CreditoMotorProperties properties;

    public RegraPrazoMaximo(CreditoMotorProperties properties) {
        this.properties = properties;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public RegraResultado avaliar(ContextoAvaliacaoCredito contexto) {
        int prazo = contexto.proposta().getPrazoMeses();
        int limite = contexto.isPessoa() ? properties.prazoMaximoPfMeses() : properties.prazoMaximoPjMeses();
        if (prazo <= limite) {
            return RegraResultado.passou(NOME);
        }
        return RegraResultado.falhou(NOME, "Prazo " + prazo + " meses acima do limite " + limite);
    }
}
