package com.dynamis.sep_api.identity.domain.model;

import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Refresh token rotativo com deteccao de reuso (Sprint 5 Task 5.3).
 *
 * <p>O token cru e devolvido uma unica vez ao cliente; aqui persistimos somente {@code tokenHash}
 * (SHA-256 hex). {@code familyId} mantem todos os tokens originados do mesmo login — se um token ja
 * marcado como {@code USADO} for reapresentado, toda a familia e revogada (reuse detection).
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, columnDefinition = "uuid")
    private UUID usuarioId;

    @Column(name = "family_id", nullable = false, columnDefinition = "uuid")
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefreshTokenStatus status;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "usado_em")
    private OffsetDateTime usadoEm;

    @Column(name = "revogado_em")
    private OffsetDateTime revogadoEm;

    protected RefreshToken() {
        // JPA
    }

    private RefreshToken(UUID id, UUID usuarioId, UUID familyId, String tokenHash, OffsetDateTime expiraEm) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiraEm = expiraEm;
        this.status = RefreshTokenStatus.ATIVO;
    }

    public static RefreshToken emitir(UUID usuarioId, UUID familyId, String tokenHash, OffsetDateTime expiraEm) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new RefreshToken(id, usuarioId, familyId, tokenHash, expiraEm);
    }

    /** Cria nova familia (login novo). */
    public static RefreshToken emitirNovoLogin(UUID usuarioId, String tokenHash, OffsetDateTime expiraEm) {
        UUID familyId = Generators.timeBasedReorderedGenerator().generate();
        return emitir(usuarioId, familyId, tokenHash, expiraEm);
    }

    public void marcarUsado() {
        this.status = RefreshTokenStatus.USADO;
        this.usadoEm = OffsetDateTime.now();
    }

    public void revogar() {
        this.status = RefreshTokenStatus.REVOGADO;
        this.revogadoEm = OffsetDateTime.now();
    }

    public void marcarExpirado() {
        this.status = RefreshTokenStatus.EXPIRADO;
    }

    public boolean estaAtivo() {
        return status == RefreshTokenStatus.ATIVO && OffsetDateTime.now().isBefore(expiraEm);
    }

    public boolean foiUsado() {
        return status == RefreshTokenStatus.USADO;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public RefreshTokenStatus getStatus() {
        return status;
    }

    public OffsetDateTime getExpiraEm() {
        return expiraEm;
    }

    public OffsetDateTime getUsadoEm() {
        return usadoEm;
    }

    public OffsetDateTime getRevogadoEm() {
        return revogadoEm;
    }
}
