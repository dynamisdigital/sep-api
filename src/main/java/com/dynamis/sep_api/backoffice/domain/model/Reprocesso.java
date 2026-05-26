package com.dynamis.sep_api.backoffice.domain.model;

import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro auditavel de cada reprocesso manual disparado pelo operador (Sprint 14 Task 14.4).
 *
 * <p>Tipos suportados:
 *
 * <ul>
 *   <li>{@code WEBHOOK}: re-dispara processamento de evento em {@code webhook_event_log}
 *       (Sprint 4).
 *   <li>{@code PROVIDER}: re-tenta chamada a provider externo (KYC/KYB/PLD/OpenFinance/Assinatura)
 *       via {@code ProviderReprocessadorDispatcher}.
 * </ul>
 *
 * <p>Apos {@code SUCESSO}/{@code FALHA} o registro e final (immutable shape; campos {@code status}
 * e {@code resultado} populados pelo use case na conclusao do reprocesso).
 */
@Entity
@Table(name = "reprocesso")
public class Reprocesso extends EntidadeAuditavel {

    public enum Tipo {
        WEBHOOK,
        PROVIDER
    }

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "item_id", columnDefinition = "uuid", updatable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20, updatable = false)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_chamada", length = 40, updatable = false)
    private TipoChamadaProvider tipoChamada;

    @Column(name = "identificador_externo", nullable = false, length = 255, updatable = false)
    private String identificadorExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusReprocesso status;

    @Column(name = "resultado", length = 4000)
    private String resultado;

    @Column(name = "data_disparo", nullable = false, updatable = false)
    private OffsetDateTime dataDisparo;

    @Column(name = "disparado_por", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID disparadoPor;

    protected Reprocesso() {}

    private Reprocesso(
            UUID id,
            UUID itemId,
            Tipo tipo,
            TipoChamadaProvider tipoChamada,
            String identificadorExterno,
            OffsetDateTime dataDisparo,
            UUID disparadoPor) {
        this.id = id;
        this.itemId = itemId;
        this.tipo = tipo;
        this.tipoChamada = tipoChamada;
        this.identificadorExterno = identificadorExterno;
        this.status = StatusReprocesso.PENDENTE;
        this.dataDisparo = dataDisparo;
        this.disparadoPor = disparadoPor;
    }

    public static Reprocesso paraWebhook(
            UUID itemId, UUID webhookEventId, OffsetDateTime dataDisparo, UUID disparadoPor) {
        Objects.requireNonNull(webhookEventId, "webhookEventId obrigatorio");
        return new Reprocesso(
                Generators.timeBasedReorderedGenerator().generate(),
                itemId,
                Tipo.WEBHOOK,
                null,
                webhookEventId.toString(),
                exigirData(dataDisparo),
                exigirOperador(disparadoPor));
    }

    public static Reprocesso paraProvider(
            UUID itemId,
            TipoChamadaProvider tipoChamada,
            UUID entidadeId,
            OffsetDateTime dataDisparo,
            UUID disparadoPor) {
        Objects.requireNonNull(tipoChamada, "tipoChamada obrigatorio");
        Objects.requireNonNull(entidadeId, "entidadeId obrigatorio");
        return new Reprocesso(
                Generators.timeBasedReorderedGenerator().generate(),
                itemId,
                Tipo.PROVIDER,
                tipoChamada,
                entidadeId.toString(),
                exigirData(dataDisparo),
                exigirOperador(disparadoPor));
    }

    public void concluirComSucesso(String resultado) {
        exigirPendente();
        this.status = StatusReprocesso.SUCESSO;
        this.resultado = resultado;
    }

    public void concluirComFalha(String resultado) {
        exigirPendente();
        this.status = StatusReprocesso.FALHA;
        this.resultado = resultado;
    }

    private void exigirPendente() {
        if (this.status != StatusReprocesso.PENDENTE) {
            throw new IllegalStateException("reprocesso em " + this.status + " nao aceita conclusao");
        }
    }

    private static OffsetDateTime exigirData(OffsetDateTime dataDisparo) {
        Objects.requireNonNull(dataDisparo, "dataDisparo obrigatoria");
        return dataDisparo;
    }

    private static UUID exigirOperador(UUID disparadoPor) {
        Objects.requireNonNull(disparadoPor, "disparadoPor obrigatorio");
        return disparadoPor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public TipoChamadaProvider getTipoChamada() {
        return tipoChamada;
    }

    public String getIdentificadorExterno() {
        return identificadorExterno;
    }

    public StatusReprocesso getStatus() {
        return status;
    }

    public String getResultado() {
        return resultado;
    }

    public OffsetDateTime getDataDisparo() {
        return dataDisparo;
    }

    public UUID getDisparadoPor() {
        return disparadoPor;
    }
}
