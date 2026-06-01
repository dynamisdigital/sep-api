package com.dynamis.sep_api.pix.domain.model;

import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.TipoPixTransferencia;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Intencao de saida Pix (Epic 15). A foundation (Sprint 19) modelou o ciclo de vida sem vinculo de
 * negocio; a Sprint 20 adiciona o desembolso assistido, vinculando a transferencia a um contrato
 * (ver {@link #criarDesembolso}).
 *
 * <p>Idempotencia garantida por {@code idempotencyKey} unica (V45). {@code externalId} so e
 * preenchido quando o provider confirma a solicitacao. A chave Pix destino <strong>nunca</strong> eh
 * persistida em claro (minimizacao de dados — CMN 4.656/2018 + LGPD): guarda-se apenas o hash
 * SHA-256 (consistencia idempotente) e a mascara (resposta/auditoria).
 */
@Entity
@Table(name = "pix_transferencia")
public class PixTransferencia extends EntidadeAuditavel {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusPixTransferencia status;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "contrato_id")
    private UUID contratoId;

    @Column(name = "proposta_id")
    private UUID propostaId;

    @Column(name = "tomador_id")
    private UUID tomadorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_transferencia", length = 40)
    private TipoPixTransferencia tipoTransferencia;

    @Column(name = "chave_destino_hash", length = 64)
    private String chaveDestinoHash;

    @Column(name = "chave_destino_mascara", length = 80)
    private String chaveDestinoMascara;

    protected PixTransferencia() {
        // JPA
    }

    private PixTransferencia(
            UUID id,
            String idempotencyKey,
            StatusPixTransferencia status,
            BigDecimal valor,
            String descricao,
            String correlationId) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.valor = valor;
        this.descricao = descricao;
        this.correlationId = correlationId;
    }

    /** Cria uma transferencia em {@link StatusPixTransferencia#CRIADA}, antes de qualquer chamada ao provider. */
    public static PixTransferencia criar(
            BigDecimal valor, String descricao, String idempotencyKey, String correlationId) {
        BigDecimal valorNormalizado = validarValor(valor);
        validarIdempotencyKey(idempotencyKey);
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new PixTransferencia(
                id, idempotencyKey, StatusPixTransferencia.CRIADA, valorNormalizado, descricao, correlationId);
    }

    /**
     * Cria uma transferencia de desembolso de contrato (Sprint 20) em {@link
     * StatusPixTransferencia#CRIADA}, antes da chamada ao provider. A chave Pix destino entra apenas
     * como hash + mascara — nunca em claro.
     */
    public static PixTransferencia criarDesembolso(
            UUID contratoId,
            UUID propostaId,
            UUID tomadorId,
            BigDecimal valor,
            String chaveDestinoHash,
            String chaveDestinoMascara,
            String idempotencyKey,
            String correlationId) {
        Objects.requireNonNull(contratoId, "contratoId obrigatorio");
        Objects.requireNonNull(propostaId, "propostaId obrigatoria");
        Objects.requireNonNull(tomadorId, "tomadorId obrigatorio");
        BigDecimal valorNormalizado = validarValor(valor);
        validarIdempotencyKey(idempotencyKey);

        String descricao = "Desembolso do contrato " + contratoId;
        PixTransferencia t = new PixTransferencia(
                Generators.timeBasedReorderedGenerator().generate(),
                idempotencyKey,
                StatusPixTransferencia.CRIADA,
                valorNormalizado,
                descricao,
                correlationId);
        t.contratoId = contratoId;
        t.propostaId = propostaId;
        t.tomadorId = tomadorId;
        t.tipoTransferencia = TipoPixTransferencia.DESEMBOLSO_CONTRATO;
        t.chaveDestinoHash = chaveDestinoHash;
        t.chaveDestinoMascara = chaveDestinoMascara;
        return t;
    }

    private static BigDecimal validarValor(BigDecimal valor) {
        Objects.requireNonNull(valor, "valor obrigatorio");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("valor deve ser positivo");
        }
        if (valor.scale() > 2) {
            throw new IllegalArgumentException("valor nao pode ter mais de 2 casas decimais");
        }
        return valor.setScale(2);
    }

    private static void validarIdempotencyKey(String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey obrigatoria");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey nao pode ser vazia");
        }
    }

    /** Registra que o provider aceitou a solicitacao, guardando o id externo retornado. */
    public void marcarSolicitada(String externalId) {
        exigirEstado(StatusPixTransferencia.CRIADA);
        Objects.requireNonNull(externalId, "externalId obrigatorio ao solicitar");
        if (externalId.isBlank()) {
            throw new IllegalArgumentException("externalId nao pode ser vazio");
        }
        this.externalId = externalId;
        this.status = StatusPixTransferencia.SOLICITADA;
    }

    /** Marca que o provider iniciou o processamento da transferencia. */
    public void marcarProcessando() {
        exigirEstado(StatusPixTransferencia.SOLICITADA);
        this.status = StatusPixTransferencia.PROCESSANDO;
    }

    /** Conclui a transferencia apos confirmacao do provider. */
    public void marcarConcluida() {
        exigirEstado(StatusPixTransferencia.SOLICITADA, StatusPixTransferencia.PROCESSANDO);
        this.status = StatusPixTransferencia.CONCLUIDA;
    }

    /** Marca falha; permitida a partir de qualquer estado ativo (antes de conclusao/cancelamento). */
    public void marcarFalhou() {
        exigirEstado(
                StatusPixTransferencia.CRIADA, StatusPixTransferencia.SOLICITADA, StatusPixTransferencia.PROCESSANDO);
        this.status = StatusPixTransferencia.FALHOU;
    }

    /** Cancela uma transferencia ainda nao solicitada ao provider. */
    public void cancelar() {
        exigirEstado(StatusPixTransferencia.CRIADA);
        this.status = StatusPixTransferencia.CANCELADA;
    }

    private void exigirEstado(StatusPixTransferencia... permitidos) {
        for (StatusPixTransferencia permitido : permitidos) {
            if (this.status == permitido) {
                return;
            }
        }
        throw new IllegalStateException("transicao invalida a partir de " + this.status);
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public StatusPixTransferencia getStatus() {
        return status;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public UUID getContratoId() {
        return contratoId;
    }

    public UUID getPropostaId() {
        return propostaId;
    }

    public UUID getTomadorId() {
        return tomadorId;
    }

    public TipoPixTransferencia getTipoTransferencia() {
        return tipoTransferencia;
    }

    public String getChaveDestinoHash() {
        return chaveDestinoHash;
    }

    public String getChaveDestinoMascara() {
        return chaveDestinoMascara;
    }
}
