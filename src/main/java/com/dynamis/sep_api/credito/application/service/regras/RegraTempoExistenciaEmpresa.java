package com.dynamis.sep_api.credito.application.service.regras;

import com.dynamis.sep_api.credito.application.service.CreditoMotorProperties;
import com.dynamis.sep_api.credito.application.service.RegraCredito;
import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Tomador PJ deve existir ha pelo menos {@code app.credito.motor.tempo-minimo-empresa-meses}
 * meses (validacao basica de risco — empresa nascente tem risco maior). Para PF retorna
 * {@code PASSOU}.
 *
 * <p>Sem data de abertura no contexto PJ -> {@code PENDENTE} (consulta CNPJ da Sprint 7 ainda nao
 * propagada).
 */
@Component
public class RegraTempoExistenciaEmpresa implements RegraCredito {

    public static final String NOME = "tempo-existencia-empresa";

    private final CreditoMotorProperties properties;

    public RegraTempoExistenciaEmpresa(CreditoMotorProperties properties) {
        this.properties = properties;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public RegraResultado avaliar(ContextoAvaliacaoCredito contexto) {
        if (contexto.isPessoa()) {
            return RegraResultado.passou(NOME);
        }
        LocalDate dataAbertura = contexto.dataAbertura();
        if (dataAbertura == null) {
            return RegraResultado.pendente(NOME, "Data de abertura ausente no contexto PJ");
        }
        long meses = ChronoUnit.MONTHS.between(dataAbertura, LocalDate.now());
        int minimo = properties.tempoMinimoEmpresaMeses();
        if (meses >= minimo) {
            return RegraResultado.passou(NOME);
        }
        return RegraResultado.falhou(NOME, "Empresa tem " + meses + " meses; minimo " + minimo);
    }
}
