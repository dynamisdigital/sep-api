package com.dynamis.sep_api.cobranca.infrastructure.adapter.notification;

import com.dynamis.sep_api.cobranca.application.port.out.TemplateNotificacaoEngine;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Adapter Thymeleaf de {@link TemplateNotificacaoEngine} (Sprint 13 Task 13.3).
 *
 * <p>Usa dois resolvers no mesmo engine: HTML pra emails (.html) e TEXT pra SMS (.txt). Cada canal
 * mapeia pra prefixo + sufixo distinto; o nome de template eh comum (ex. {@code cobranca-amigavel})
 * e a extensao decorre do canal.
 *
 * <p>{@link TemplateEngine} eh standalone (sem starter Spring Boot) — o mesmo padrao do
 * {@code ThymeleafTemplateContratoEngine} (Sprint 10).
 */
@Component
public class ThymeleafTemplateNotificacaoEngine implements TemplateNotificacaoEngine {

    private static final String PREFIX = "templates/notificacoes/";
    private static final String SUFIXO_EMAIL = "-email.html";
    private static final String SUFIXO_SMS = "-sms.txt";

    private final TemplateEngine engine;

    public ThymeleafTemplateNotificacaoEngine() {
        this.engine = new TemplateEngine();
        engine.addTemplateResolver(resolver(PREFIX, SUFIXO_EMAIL, TemplateMode.HTML, 1));
        engine.addTemplateResolver(resolver(PREFIX, SUFIXO_SMS, TemplateMode.TEXT, 2));
    }

    @Override
    public String renderizar(CanalNotificacao canal, String template, Map<String, Object> variaveis) {
        String nomeCompleto = nomeCompleto(canal, template);
        Context ctx = new Context();
        if (variaveis != null) {
            variaveis.forEach(ctx::setVariable);
        }
        try {
            String conteudo = engine.process(nomeCompleto, ctx);
            if (conteudo == null || conteudo.isBlank()) {
                throw new TemplateNotificacaoException("template " + nomeCompleto + " gerou conteudo vazio");
            }
            return conteudo;
        } catch (TemplateInputException e) {
            throw new TemplateNotificacaoException("template nao encontrado: " + nomeCompleto, e);
        }
    }

    private static String nomeCompleto(CanalNotificacao canal, String template) {
        return switch (canal) {
            case EMAIL -> template + SUFIXO_EMAIL.substring(0, SUFIXO_EMAIL.lastIndexOf('.'));
            case SMS -> template + SUFIXO_SMS.substring(0, SUFIXO_SMS.lastIndexOf('.'));
        };
    }

    private static ClassLoaderTemplateResolver resolver(
            String prefix, String suffixComExtensao, TemplateMode mode, int order) {
        // O suffix inclui extensao + parte distintiva do canal (ex. "-email.html").
        // Como Thymeleaf concatena `prefix + nome + suffix`, o nome passado em renderizar
        // ja foi normalizado para conter o sufixo do canal sem extensao (ex. "cobranca-amigavel-email").
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(prefix);
        resolver.setSuffix(suffixComExtensao.substring(suffixComExtensao.lastIndexOf('.')));
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        resolver.setOrder(order);
        // Cada resolver soh processa templates que casem com o nome esperado pelo canal
        // (sufixo `-email` ou `-sms`) — evita ambiguidade entre HTML e TEXT.
        resolver.setResolvablePatterns(
                java.util.Set.of("*" + suffixComExtensao.substring(0, suffixComExtensao.lastIndexOf('.'))));
        return resolver;
    }
}
