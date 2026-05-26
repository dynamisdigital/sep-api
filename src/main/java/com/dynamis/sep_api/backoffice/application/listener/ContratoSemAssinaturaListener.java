package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.job.BackofficeVerificadorProperties;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job-driven: cria item {@code CONTRATO_NAO_ASSINADO} para contratos parados em {@code ACEITO}
 * alem do threshold (default 48h) sem progredir para {@code EM_ASSINATURA} (Sprint 14 Task 14.2).
 */
@Component
public class ContratoSemAssinaturaListener {

    private final ContratoRepository contratoRepository;
    private final CriarItemFilaOperacionalService criarItem;
    private final BackofficeVerificadorProperties properties;
    private final Clock clock;

    public ContratoSemAssinaturaListener(
            ContratoRepository contratoRepository,
            CriarItemFilaOperacionalService criarItem,
            BackofficeVerificadorProperties properties,
            Clock clock) {
        this.contratoRepository = contratoRepository;
        this.criarItem = criarItem;
        this.properties = properties;
        this.clock = clock;
    }

    public void verificar() {
        OffsetDateTime corte = OffsetDateTime.now(clock).minusHours(properties.contratoAceitoHoras());
        List<Contrato> parados =
                contratoRepository.findByStatusAndDataModificacaoBefore(StatusFormalizacao.ACEITO, corte);

        for (Contrato c : parados) {
            criarItem.criarSeAusente(new CriarItemCommand(
                    TipoItemFila.CONTRATO_NAO_ASSINADO,
                    PrioridadeItem.MEDIA,
                    TipoEntidadeReferenciada.CONTRATO,
                    c.getId(),
                    "Contrato aceito sem progredir para assinatura",
                    "Sem evolucao ha mais de " + properties.contratoAceitoHoras() + "h"));
        }
    }
}
