package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.job.BackofficeVerificadorProperties;
import com.dynamis.sep_api.backoffice.application.port.out.PendenciaCreditoQueryPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.PropostaPendenciaView;
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
 * Job-driven: invocado pelo {@link com.dynamis.sep_api.backoffice.application.job.VerificadorPendenciasJob}.
 * Cria item {@code PROPOSTA_PENDENTE} para propostas paradas em {@code EM_ANALISE} alem do
 * threshold (Sprint 14 Task 14.2).
 *
 * <p>Acesso ao dominio de {@code credito} via {@link PendenciaCreditoQueryPort} (port/adapter)
 * pra preservar fronteira hexagonal entre modulos (fix review manual Task 14.2).
 */
@Component
public class PropostaPendenciaListener {

    private final PendenciaCreditoQueryPort creditoQuery;
    private final CriarItemFilaOperacionalService criarItem;
    private final BackofficeVerificadorProperties properties;
    private final Clock clock;

    public PropostaPendenciaListener(
            PendenciaCreditoQueryPort creditoQuery,
            CriarItemFilaOperacionalService criarItem,
            BackofficeVerificadorProperties properties,
            Clock clock) {
        this.creditoQuery = creditoQuery;
        this.criarItem = criarItem;
        this.properties = properties;
        this.clock = clock;
    }

    public void verificar() {
        OffsetDateTime corte = OffsetDateTime.now(clock).minusHours(properties.propostaPendenciaHoras());
        List<PropostaPendenciaView> paradas = creditoQuery.propostasParadasEmAnalise(corte);

        for (PropostaPendenciaView p : paradas) {
            criarItem.criarSeAusente(new CriarItemCommand(
                    TipoItemFila.PROPOSTA_PENDENTE,
                    PrioridadeItem.MEDIA,
                    TipoEntidadeReferenciada.PROPOSTA,
                    p.propostaId(),
                    "Proposta de credito parada em EM_ANALISE",
                    "Sem evolucao ha mais de " + properties.propostaPendenciaHoras() + "h"));
        }
    }
}
