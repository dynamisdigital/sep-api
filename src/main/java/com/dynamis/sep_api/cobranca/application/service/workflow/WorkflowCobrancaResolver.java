package com.dynamis.sep_api.cobranca.application.service.workflow;

import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolve qual etapa do workflow se aplica a um dado dia de atraso (Sprint 13 Task 13.4).
 *
 * <p>Carrega in-memory as etapas configuradas via {@link WorkflowCobrancaProperties} e indexa por
 * {@code dia}. Estrategia atual eh match exato — o {@code EscaladorCobrancaJob} re-roda
 * diariamente, entao cada dia ativo do workflow processa uma unica vez quando o contador de
 * atraso alcanca o valor configurado.
 */
@Component
public class WorkflowCobrancaResolver {

    private final Map<Integer, EtapaWorkflow> etapasPorDia;

    public WorkflowCobrancaResolver(WorkflowCobrancaProperties properties) {
        this.etapasPorDia = properties.diasAtraso().stream()
                .collect(Collectors.toUnmodifiableMap(
                        WorkflowCobrancaProperties.EtapaProperties::dia, WorkflowCobrancaResolver::converter));
    }

    public Optional<EtapaWorkflow> etapaParaDia(int diasAtraso) {
        return Optional.ofNullable(etapasPorDia.get(diasAtraso));
    }

    private static EtapaWorkflow converter(WorkflowCobrancaProperties.EtapaProperties props) {
        List<NotificacaoEtapa> notificacoes = props.notificacoes().stream()
                .map(WorkflowCobrancaResolver::parseNotificacao)
                .toList();
        return new EtapaWorkflow(
                props.dia(),
                notificacoes,
                props.flagContatoManual(),
                props.escalonarBackoffice(),
                props.marcarInadimplente());
    }

    /**
     * Converte o nome de notificacao do YAML (ex. {@code email-amigavel}) em par {@code (canal,
     * template)}. Convencao: o prefixo antes do primeiro {@code -} define o canal, o sufixo eh
     * anexado a {@code cobranca-} pra formar o nome do template do {@code TemplateNotificacaoEngine}.
     */
    static NotificacaoEtapa parseNotificacao(String entrada) {
        if (entrada == null || entrada.isBlank()) {
            throw new IllegalArgumentException("nome de notificacao vazio");
        }
        int hyphen = entrada.indexOf('-');
        if (hyphen <= 0 || hyphen == entrada.length() - 1) {
            throw new IllegalArgumentException("nome de notificacao malformado: " + entrada);
        }
        String prefixo = entrada.substring(0, hyphen);
        String sufixo = entrada.substring(hyphen + 1);
        CanalNotificacao canal =
                switch (prefixo) {
                    case "email" -> CanalNotificacao.EMAIL;
                    case "sms" -> CanalNotificacao.SMS;
                    default -> throw new IllegalArgumentException("canal desconhecido em '" + entrada + "'");
                };
        return new NotificacaoEtapa(canal, "cobranca-" + sufixo);
    }

    /** Etapa do workflow resolvida e pronta pra uso pelo {@code EscalarCobrancaUseCase}. */
    public record EtapaWorkflow(
            int dia,
            List<NotificacaoEtapa> notificacoes,
            boolean flagContatoManual,
            boolean escalonarBackoffice,
            boolean marcarInadimplente) {}

    public record NotificacaoEtapa(CanalNotificacao canal, String template) {}
}
