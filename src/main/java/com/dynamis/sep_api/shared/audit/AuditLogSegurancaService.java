package com.dynamis.sep_api.shared.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Helper para gravacao de eventos no {@code audit_log_seguranca} (Sprint 5 Task 5.7). Centraliza a
 * construcao do {@link AuditLogSeguranca} e mantem os use cases simples.
 */
@Service
public class AuditLogSegurancaService {

    private final AuditLogSegurancaRepository repository;

    public AuditLogSegurancaService(AuditLogSegurancaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void gravar(TipoEventoSeguranca tipo, UUID usuarioId) {
        repository.save(AuditLogSeguranca.registrar(tipo, usuarioId, null, null, "{}"));
    }

    @Transactional
    public void gravar(TipoEventoSeguranca tipo, UUID usuarioId, String detalhesJson) {
        repository.save(AuditLogSeguranca.registrar(tipo, usuarioId, null, null, detalhesJson));
    }

    @Transactional
    public void gravar(TipoEventoSeguranca tipo, UUID usuarioId, String ip, String userAgent, String detalhesJson) {
        repository.save(AuditLogSeguranca.registrar(tipo, usuarioId, ip, userAgent, detalhesJson));
    }
}
