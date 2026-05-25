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
import java.util.Set;

/**
 * Adapter Thymeleaf de {@link TemplateNotificacaoEngine} (Sprint 13 Task 13.3).
 *
 * <p>Usa dois resolvers no mesmo engine: HTML pra emails (.html) e TEXT pra SMS (.txt). Cada canal
 * mapeia pra prefixo + sufixo distinto; o nome de template eh comum (ex. {@code cobranca-amigavel})
 * e o sufixo do canal eh anexado antes de bater no resolver.
 *
 * <p>{@link TemplateEngine} eh standalone (sem starter Spring Boot) — o mesmo padrao do
 * {@code ThymeleafTemplateContratoEngine} (Sprint 10).
 */
@Component
public class ThymeleafTemplateNotificacaoEngine implements TemplateNotificacaoEngine {

    private static final String PREFIX = "templates/notificacoes/";
    private static final String SUFIXO_EMAIL = "-email";
    private static final String SUFIXO_SMS = "-sms";
    private static final String EXT_HTML = ".html";
    private static final String EXT_TXT = ".txt";

    private final TemplateEngine engine;

    public ThymeleafTemplateNotificacaoEngine() {
        this.engine = new TemplateEngine();
        engine.addTemplateResolver(resolver(EXT_HTML, TemplateMode.HTML, SUFIXO_EMAIL, 1));
        engine.addTemplateResolver(resolver(EXT_TXT, TemplateMode.TEXT, SUFIXO_SMS, 2));
    }

    @Override
    public String renderizar(CanalNotificacao canal, String template, Map<String, Object> variaveis) {
        String nomeCompleto = template + sufixoCanal(canal);
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

    private static String sufixoCanal(CanalNotificacao canal) {
        return switch (canal) {
            case EMAIL -> SUFIXO_EMAIL;
            case SMS -> SUFIXO_SMS;
        };
    }

    private static ClassLoaderTemplateResolver resolver(
            String extensao, TemplateMode mode, String sufixoNome, int order) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(PREFIX);
        resolver.setSuffix(extensao);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        resolver.setOrder(order);
        // Cada resolver soh processa templates do seu canal (`*-email` ou `*-sms`) —
        // evita ambiguidade entre HTML e TEXT quando ambos resolvers estao no engine.
        resolver.setResolvablePatterns(Set.of("*" + sufixoNome));
        return resolver;
    }
}
