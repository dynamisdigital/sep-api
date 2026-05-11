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
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireStepUp {}
