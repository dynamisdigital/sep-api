package com.dynamis.sep_api.identity.infrastructure.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Variante <strong>estrita</strong> de {@link RequireStepUp} (Sprint 20): exige step-up token valido
 * mesmo quando o usuario nao tem MFA habilitado — <strong>sem o bypass</strong> de migracao do
 * {@link StepUpEnforcementAspect}.
 *
 * <p>Uso em operacoes financeiras de alto risco (ex.: desembolso Pix) onde liberar a operacao a um
 * operador sem MFA seria inaceitavel. Na pratica, exige que o operador (FINANCEIRO/ADMIN) tenha MFA
 * ativo para conseguir produzir o step-up token; do contrario recebe 403.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireStepUpEstrito {}
