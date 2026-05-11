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
 * Token efemero (5 min) emitido apos step-up MFA — habilita o usuario a executar uma operacao
 * sensivel (alterar senha, desabilitar MFA, futuras operacoes Pix/contratos). PRD §14, Sprint 5
 * Task 5.6.
 */
@Entity
@Table(name = "step_up_token")
public class StepUpToken extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, columnDefinition = "uuid")
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "usado", nullable = false)
    private boolean usado;

    @Column(name = "usado_em")
    private OffsetDateTime usadoEm;

    protected StepUpToken() {
        // JPA
    }

    private StepUpToken(UUID id, UUID usuarioId, String tokenHash, OffsetDateTime expiraEm) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiraEm = expiraEm;
        this.usado = false;
    }

    public static StepUpToken emitir(UUID usuarioId, String tokenHash, OffsetDateTime expiraEm) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new StepUpToken(id, usuarioId, tokenHash, expiraEm);
    }

    public void marcarUsado() {
        if (this.usado) {
            throw new IllegalStateException("Step-up token ja foi usado.");
        }
        this.usado = true;
        this.usadoEm = OffsetDateTime.now();
    }

    public boolean estaValido() {
        return !usado && OffsetDateTime.now().isBefore(expiraEm);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiraEm() {
        return expiraEm;
    }

    public boolean isUsado() {
        return usado;
    }

    public OffsetDateTime getUsadoEm() {
        return usadoEm;
    }
}
