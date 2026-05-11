package com.dynamis.sep_api.identity.domain.model;

import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Backup code TOTP de uso unico. Persistencia apenas como hash BCrypt; o codigo claro e exibido ao
 * usuario uma unica vez no setup do MFA (PRD §14, Spec 005 Task 5.2).
 */
@Entity
@Table(name = "usuario_backup_code")
public class UsuarioBackupCode extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, columnDefinition = "uuid")
    private UUID usuarioId;

    @Column(name = "codigo_hash", nullable = false, length = 255)
    private String codigoHash;

    @Column(name = "usado", nullable = false)
    private boolean usado;

    @Column(name = "usado_em")
    private OffsetDateTime usadoEm;

    protected UsuarioBackupCode() {
        // JPA
    }

    private UsuarioBackupCode(UUID id, UUID usuarioId, String codigoHash) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.codigoHash = codigoHash;
        this.usado = false;
    }

    public static UsuarioBackupCode criar(UUID usuarioId, String codigoHash) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new UsuarioBackupCode(id, usuarioId, codigoHash);
    }

    public void marcarUsado() {
        if (this.usado) {
            throw new IllegalStateException("Backup code ja foi usado.");
        }
        this.usado = true;
        this.usadoEm = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getCodigoHash() {
        return codigoHash;
    }

    public boolean isUsado() {
        return usado;
    }

    public OffsetDateTime getUsadoEm() {
        return usadoEm;
    }
}
