package com.dynamis.sep_api.credito.application.service.regras;

import com.dynamis.sep_api.credito.application.service.CreditoMotorProperties;
import com.dynamis.sep_api.credito.application.service.RegraCredito;
import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;

/**
 * Tomador pessoa fisica deve ter pelo menos {@code app.credito.motor.idade-minima-pessoa} anos na
 * data da avaliacao. Para tomador PJ a regra retorna {@code PASSOU} (nao aplicavel).
 *
 * <p>Se a data de nascimento nao estiver disponivel no contexto PF, retorna {@code PENDENTE} —
 * motor penaliza score sem rejeitar.
 */
@Component
public class RegraIdadeMinimaPessoa implements RegraCredito {

    public static final String NOME = "idade-minima-pessoa";

    private final CreditoMotorProperties properties;

    public RegraIdadeMinimaPessoa(CreditoMotorProperties properties) {
        this.properties = properties;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public RegraResultado avaliar(ContextoAvaliacaoCredito contexto) {
        if (contexto.isEmpresa()) {
            return RegraResultado.passou(NOME);
        }
        LocalDate dataNascimento = contexto.dataNascimento();
        if (dataNascimento == null) {
            return RegraResultado.pendente(NOME, "Data de nascimento ausente no contexto PF");
        }
        int idade = Period.between(dataNascimento, LocalDate.now()).getYears();
        int minimo = properties.idadeMinimaPessoa();
        if (idade >= minimo) {
            return RegraResultado.passou(NOME);
        }
        return RegraResultado.falhou(NOME, "Idade " + idade + " menor que minima " + minimo);
    }
}
