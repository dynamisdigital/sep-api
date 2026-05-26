package com.dynamis.sep_api.backoffice.application.job;

import com.dynamis.sep_api.backoffice.application.listener.ContratoSemAssinaturaListener;
import com.dynamis.sep_api.backoffice.application.listener.PropostaPendenciaListener;
import com.dynamis.sep_api.backoffice.application.listener.WebhookFalhouListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job consolidador (Sprint 14 Task 14.2): a cada 15 minutos varre tres fontes de pendencia
 * baseadas em tempo (propostas paradas, contratos sem assinatura, webhooks nao processados) e
 * delega criacao de itens aos listeners job-driven. Falha em um listener nao para os demais.
 *
 * <p>Cron padrao {@code 0 *&#47;15 * * * *}; configuravel via
 * {@code app.backoffice.verificador.cron}. Pode ser desabilitado em testes via
 * {@code app.scheduling-habilitado=false} (mecanismo herdado da Sprint 13).
 */
@Component
@ConditionalOnProperty(
        name = "app.backoffice.verificador.scheduling-habilitado",
        havingValue = "true",
        matchIfMissing = true)
public class VerificadorPendenciasJob {

    private static final Logger LOG = LoggerFactory.getLogger(VerificadorPendenciasJob.class);

    private final PropostaPendenciaListener propostaPendenciaListener;
    private final ContratoSemAssinaturaListener contratoSemAssinaturaListener;
    private final WebhookFalhouListener webhookFalhouListener;

    public VerificadorPendenciasJob(
            PropostaPendenciaListener propostaPendenciaListener,
            ContratoSemAssinaturaListener contratoSemAssinaturaListener,
            WebhookFalhouListener webhookFalhouListener) {
        this.propostaPendenciaListener = propostaPendenciaListener;
        this.contratoSemAssinaturaListener = contratoSemAssinaturaListener;
        this.webhookFalhouListener = webhookFalhouListener;
    }

    @Scheduled(cron = "${app.backoffice.verificador.cron:0 */15 * * * *}")
    public void executar() {
        executarIsolado("PropostaPendenciaListener", propostaPendenciaListener::verificar);
        executarIsolado("ContratoSemAssinaturaListener", contratoSemAssinaturaListener::verificar);
        executarIsolado("WebhookFalhouListener", webhookFalhouListener::verificar);
    }

    private void executarIsolado(String nome, Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException ex) {
            LOG.error("Verificador {} falhou; demais checks seguem", nome, ex);
        }
    }
}
