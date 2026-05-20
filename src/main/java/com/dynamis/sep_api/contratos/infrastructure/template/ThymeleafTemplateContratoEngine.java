package com.dynamis.sep_api.contratos.infrastructure.template;

import com.dynamis.sep_api.contratos.application.port.out.TemplateContratoEngine;
import com.dynamis.sep_api.contratos.application.port.out.TemplateContratoException;
import com.dynamis.sep_api.contratos.application.port.out.dto.TemplateContratoRequest;
import com.dynamis.sep_api.contratos.application.port.out.dto.TemplateContratoResponse;
import com.dynamis.sep_api.contratos.application.service.ClausulaContratoParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Adapter Thymeleaf da {@link TemplateContratoEngine}. Carrega templates do classpath em modo
 * {@code TEXT} (nao HTML — contratos sao texto plano nesta sprint), renderiza com as variaveis
 * fornecidas e delega o parsing de clausulas ao {@link ClausulaContratoParser}.
 *
 * <p>Usamos {@link TemplateEngine} standalone (sem starter Spring Boot) para evitar plugar view
 * resolver em Spring MVC; o engine fica encapsulado no modulo {@code contratos}.
 */
@Component
public class ThymeleafTemplateContratoEngine implements TemplateContratoEngine {

    private final TemplateEngine engine;
    private final ClausulaContratoParser clausulaParser;

    public ThymeleafTemplateContratoEngine(
            ClausulaContratoParser clausulaParser,
            @Value("${app.contratos.template-prefix:templates/}") String templatePrefix,
            @Value("${app.contratos.template-suffix:.txt}") String templateSuffix) {
        this.clausulaParser = clausulaParser;
        this.engine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(templatePrefix);
        resolver.setSuffix(templateSuffix);
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        this.engine.setTemplateResolver(resolver);
    }

    @Override
    public TemplateContratoResponse renderizar(TemplateContratoRequest request) {
        String templateName = request.tipo().templatePath();
        Context ctx = new Context();
        request.variaveis().forEach(ctx::setVariable);
        String conteudo;
        try {
            conteudo = engine.process(templateName, ctx);
        } catch (TemplateInputException e) {
            throw new TemplateContratoException("Template nao encontrado ou invalido: " + templateName, e);
        } catch (RuntimeException e) {
            throw new TemplateContratoException("Falha ao renderizar template " + templateName, e);
        }
        if (conteudo == null || conteudo.isBlank()) {
            throw new TemplateContratoException("Template " + templateName + " gerou conteudo vazio");
        }
        var clausulas = clausulaParser.parse(conteudo);
        if (clausulas.isEmpty()) {
            throw new TemplateContratoException(
                    "Template "
                            + templateName
                            + " gerou contrato sem clausulas — marcadores 'CLAUSULA N - TITULO' ausentes ou clausulasPadrao vazio");
        }
        return new TemplateContratoResponse(conteudo, clausulas);
    }
}
