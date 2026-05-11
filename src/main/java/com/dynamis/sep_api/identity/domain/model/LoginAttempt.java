package com.dynamis.sep_api.identity.domain.model;

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
 * Registro de cada tentativa de login. Alimenta {@code LockoutService} e o audit log de seguranca
 * (Sprint 5 Task 5.4).
 *
 * <p>Nao estende {@link com.dynamis.sep_api.shared.audit.EntidadeAuditavel} porque o evento e
 * imutavel e tem somente {@code dataTentativa}.
 */
@Entity
@Table(name = "login_attempt")
public class LoginAttempt {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario_id", columnDefinition = "uuid")
    private UUID usuarioId;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private LoginAttemptStatus status;

    @Column(name = "data_tentativa", nullable = false)
    private OffsetDateTime dataTentativa;

    protected LoginAttempt() {
        // JPA
    }

    private LoginAttempt(
            UUID id,
            UUID usuarioId,
            String username,
            String ip,
            String userAgent,
            LoginAttemptStatus status,
            OffsetDateTime dataTentativa) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.username = username;
        this.ip = ip;
        this.userAgent = userAgent;
        this.status = status;
        this.dataTentativa = dataTentativa;
    }

    public static LoginAttempt registrar(
            UUID usuarioId, String username, String ip, String userAgent, LoginAttemptStatus status) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new LoginAttempt(id, usuarioId, username, ip, userAgent, status, OffsetDateTime.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getUsername() {
        return username;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public LoginAttemptStatus getStatus() {
        return status;
    }

    public OffsetDateTime getDataTentativa() {
        return dataTentativa;
    }
}
