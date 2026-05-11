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
 * Secret TOTP de um usuario (1:1 com {@code usuario}). O campo {@code secretCifrado} guarda o
 * secret Base32 ja cifrado em repouso — nunca persistir em claro (PRD §14, ADR 0010).
 */
@Entity
@Table(name = "usuario_totp_secret")
public class UsuarioTotpSecret extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, columnDefinition = "uuid")
    private UUID usuarioId;

    @Column(name = "secret_cifrado", nullable = false, length = 500)
    private String secretCifrado;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MfaStatus status;

    @Column(name = "data_ativacao")
    private OffsetDateTime dataAtivacao;

    protected UsuarioTotpSecret() {
        // JPA
    }

    private UsuarioTotpSecret(UUID id, UUID usuarioId, String secretCifrado, MfaStatus status) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.secretCifrado = secretCifrado;
        this.status = status;
    }

    public static UsuarioTotpSecret iniciar(UUID usuarioId, String secretCifrado) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new UsuarioTotpSecret(id, usuarioId, secretCifrado, MfaStatus.PENDENTE);
    }

    public void ativar() {
        this.status = MfaStatus.ATIVO;
        this.dataAtivacao = OffsetDateTime.now();
    }

    public void desabilitar() {
        this.status = MfaStatus.DESABILITADO;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getSecretCifrado() {
        return secretCifrado;
    }

    public MfaStatus getStatus() {
        return status;
    }

    public OffsetDateTime getDataAtivacao() {
        return dataAtivacao;
    }
}
