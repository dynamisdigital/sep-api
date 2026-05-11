package com.dynamis.sep_api.shared.audit;

import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de seguranca persistido na tabela {@code audit_log_seguranca} (Sprint 5 Task 5.7).
 *
 * <p>Separada da auditoria JPA generica ({@link EntidadeAuditavel}) porque guarda eventos do
 * dominio de seguranca (LOGIN_OK/FAIL, TOTP_*, LOCKOUT, REFRESH_REUSE_DETECTED, etc.) e tem
 * retencao propria (>= 90 dias).
 */
@Entity
@Table(name = "audit_log_seguranca")
public class AuditLogSeguranca {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 50)
    private TipoEventoSeguranca tipo;

    @Column(name = "usuario_id", columnDefinition = "uuid")
    private UUID usuarioId;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalhes", columnDefinition = "jsonb")
    private String detalhes;

    @Column(name = "data_evento", nullable = false)
    private OffsetDateTime dataEvento;

    protected AuditLogSeguranca() {
        // JPA
    }

    private AuditLogSeguranca(
            UUID id,
            TipoEventoSeguranca tipo,
            UUID usuarioId,
            String ip,
            String userAgent,
            String detalhes,
            OffsetDateTime dataEvento) {
        this.id = id;
        this.tipo = tipo;
        this.usuarioId = usuarioId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.detalhes = detalhes;
        this.dataEvento = dataEvento;
    }

    public static AuditLogSeguranca registrar(
            TipoEventoSeguranca tipo, UUID usuarioId, String ip, String userAgent, String detalhesJson) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new AuditLogSeguranca(id, tipo, usuarioId, ip, userAgent, detalhesJson, OffsetDateTime.now());
    }

    public UUID getId() {
        return id;
    }

    public TipoEventoSeguranca getTipo() {
        return tipo;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getDetalhes() {
        return detalhes;
    }

    public OffsetDateTime getDataEvento() {
        return dataEvento;
    }
}
