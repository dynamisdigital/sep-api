package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.job.BackofficeVerificadorProperties;
import com.dynamis.sep_api.backoffice.application.port.out.PendenciaContratoQueryPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ContratoPendenciaView;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job-driven: cria item {@code CONTRATO_NAO_ASSINADO} para contratos parados em {@code ACEITO}
 * alem do threshold (default 48h) sem progredir para {@code EM_ASSINATURA} (Sprint 14 Task 14.2).
 *
 * <p>Acesso a {@code contratos} via {@link PendenciaContratoQueryPort} (port/adapter) pra
 * preservar fronteira hexagonal (fix review manual Task 14.2).
 */
@Component
public class ContratoSemAssinaturaListener {

    private final PendenciaContratoQueryPort contratoQuery;
    private final CriarItemFilaOperacionalService criarItem;
    private final BackofficeVerificadorProperties properties;
    private final Clock clock;

    public ContratoSemAssinaturaListener(
            PendenciaContratoQueryPort contratoQuery,
            CriarItemFilaOperacionalService criarItem,
            BackofficeVerificadorProperties properties,
            Clock clock) {
        this.contratoQuery = contratoQuery;
        this.criarItem = criarItem;
        this.properties = properties;
        this.clock = clock;
    }

    public void verificar() {
        OffsetDateTime corte = OffsetDateTime.now(clock).minusHours(properties.contratoAceitoHoras());
        List<ContratoPendenciaView> parados = contratoQuery.contratosAceitosSemAssinatura(corte);

        for (ContratoPendenciaView c : parados) {
            criarItem.criarSeAusente(new CriarItemCommand(
                    TipoItemFila.CONTRATO_NAO_ASSINADO,
                    PrioridadeItem.MEDIA,
                    TipoEntidadeReferenciada.CONTRATO,
                    c.contratoId(),
                    "Contrato aceito sem progredir para assinatura",
                    "Sem evolucao ha mais de " + properties.contratoAceitoHoras() + "h"));
        }
    }
}
