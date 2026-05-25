package com.dynamis.sep_api.cobranca.application.service.workflow;

import com.dynamis.sep_api.cobranca.domain.model.WorkflowCobranca;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.WorkflowCobrancaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicializa a tabela {@code workflow_cobranca} a partir das etapas do YAML quando o nome default
 * nao tem nenhuma etapa ativa persistida (Sprint 13 Task 13.4).
 *
 * <p>Fix review manual: o step 13.4 pede workflow ativo persistido inicializado por YAML. O
 * {@link WorkflowCobrancaResolver} ainda le da memoria (YAML eh fonte de verdade nesta sprint),
 * mas a tabela passa a ter o snapshot inicial — disponivel para auditoria, edicao via backoffice
 * (Sprint 14) e migracao futura para fonte primaria.
 *
 * <p>Idempotente: so popula se nao houver etapa ativa para o nome {@code default}. Em testes que
 * limpam o banco entre execucoes, re-popula automaticamente.
 *
 * <p>Pode ser desligado via {@code app.cobranca.workflow-seed-habilitado=false}.
 */
@Component
@ConditionalOnProperty(name = "app.cobranca.workflow-seed-habilitado", havingValue = "true", matchIfMissing = true)
public class WorkflowCobrancaSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCobrancaSeeder.class);
    private static final String NOME_DEFAULT = "default";

    private final WorkflowCobrancaProperties properties;
    private final WorkflowCobrancaRepository repository;

    public WorkflowCobrancaSeeder(WorkflowCobrancaProperties properties, WorkflowCobrancaRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!repository.findByNomeAndAtivoTrueOrderByDiaAtrasoAsc(NOME_DEFAULT).isEmpty()) {
            log.debug("WorkflowCobrancaSeeder: tabela ja tem etapas ativas para '{}'; skip", NOME_DEFAULT);
            return;
        }
        for (WorkflowCobrancaProperties.EtapaProperties etapa : properties.diasAtraso()) {
            repository.save(WorkflowCobranca.criar(
                    NOME_DEFAULT,
                    etapa.dia(),
                    etapa.notificacoes(),
                    etapa.flagContatoManual(),
                    etapa.escalonarBackoffice(),
                    etapa.marcarInadimplente()));
        }
        log.info(
                "WorkflowCobrancaSeeder: populou {} etapas de workflow '{}'",
                properties.diasAtraso().size(),
                NOME_DEFAULT);
    }
}
