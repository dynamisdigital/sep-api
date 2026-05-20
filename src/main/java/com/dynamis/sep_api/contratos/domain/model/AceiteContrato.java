package com.dynamis.sep_api.contratos.domain.model;

import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Aceite registrado por um tomador sobre uma {@link VersaoContrato} especifica. Evidencia tecnica:
 * IP origem (max 45 chars) e user-agent truncado em 500 chars. Unico por versao (1:1).
 */
@Entity
@Table(name = "aceite_contrato")
public class AceiteContrato {

    private static final int USER_AGENT_MAX = 500;

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "versao_id", nullable = false, unique = true, updatable = false)
    private VersaoContrato versao;

    @Column(name = "tomador_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID tomadorId;

    @Column(name = "data_aceite", nullable = false, updatable = false)
    private OffsetDateTime dataAceite;

    @Column(name = "ip_origem", length = 45, updatable = false)
    private String ipOrigem;

    @Column(name = "user_agent_origem", length = USER_AGENT_MAX, updatable = false)
    private String userAgentOrigem;

    protected AceiteContrato() {}

    private AceiteContrato(
            UUID id,
            VersaoContrato versao,
            UUID tomadorId,
            OffsetDateTime dataAceite,
            String ipOrigem,
            String userAgentOrigem) {
        this.id = id;
        this.versao = versao;
        this.tomadorId = tomadorId;
        this.dataAceite = dataAceite;
        this.ipOrigem = ipOrigem;
        this.userAgentOrigem = userAgentOrigem;
    }

    public static AceiteContrato registrar(
            VersaoContrato versao, UUID tomadorId, String ipOrigem, String userAgentOrigem) {
        Objects.requireNonNull(versao, "versao obrigatoria");
        Objects.requireNonNull(tomadorId, "tomadorId obrigatorio");
        String userAgentTruncado = truncar(userAgentOrigem);
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new AceiteContrato(id, versao, tomadorId, OffsetDateTime.now(), ipOrigem, userAgentTruncado);
    }

    private static String truncar(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > USER_AGENT_MAX ? userAgent.substring(0, USER_AGENT_MAX) : userAgent;
    }

    public UUID getId() {
        return id;
    }

    public VersaoContrato getVersao() {
        return versao;
    }

    public UUID getTomadorId() {
        return tomadorId;
    }

    public OffsetDateTime getDataAceite() {
        return dataAceite;
    }

    public String getIpOrigem() {
        return ipOrigem;
    }

    public String getUserAgentOrigem() {
        return userAgentOrigem;
    }
}
