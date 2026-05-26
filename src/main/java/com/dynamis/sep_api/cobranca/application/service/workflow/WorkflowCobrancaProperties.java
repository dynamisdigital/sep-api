package com.dynamis.sep_api.cobranca.application.service.workflow;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binding tipado das properties {@code app.cobranca.workflow.*} (Sprint 13 Task 13.4).
 *
 * <p>Define as etapas do workflow de cobranca por dia de atraso. YAML eh fonte de verdade — a
 * tabela {@code workflow_cobranca} (Task 13.2) fica disponivel pra evolucao futura (edicao via
 * backoffice na Sprint 14+) e nao eh consumida nesta sprint.
 */
@ConfigurationProperties(prefix = "app.cobranca.workflow")
public record WorkflowCobrancaProperties(List<EtapaProperties> diasAtraso) {

    public WorkflowCobrancaProperties {
        if (diasAtraso == null) {
            diasAtraso = List.of();
        }
    }

    public record EtapaProperties(
            int dia,
            List<String> notificacoes,
            boolean flagContatoManual,
            boolean escalonarBackoffice,
            boolean marcarInadimplente) {

        public EtapaProperties {
            if (dia < 0) {
                throw new IllegalArgumentException("dia nao pode ser negativo: " + dia);
            }
            if (notificacoes == null) {
                notificacoes = List.of();
            }
        }
    }
}
