package com.dynamis.sep_api.shared.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita o JPA Auditing apontando para o {@link AuditorAwareImpl} (bean qualificado como
 * {@code auditorAware}).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {}
