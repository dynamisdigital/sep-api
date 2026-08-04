package com.dynamis.sep_api.identity.infrastructure.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca endpoints sensiveis que exigem step-up token valido apresentado em
 * {@code X-Step-Up-Token} (Sprint 5 Task 5.6). Interceptado por
 * {@link StepUpEnforcementAspect}.
 */
// Somente METHOD, como RequireStepUpEstrito: o pointcut do aspect e @annotation(...) e o
// OperationCustomizer usa getMethodAnnotation — nenhum dos dois olha a classe. Aceitar TYPE
// permitia uma colocacao que nao protege nem documenta nada (Sprint 34 Task 34.6).
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireStepUp {}
