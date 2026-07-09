package com.dynamis.sep_api.credores.domain.model;

import com.dynamis.sep_api.credores.domain.vo.StatusAporteCredora;
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
 * Aporte assistido da credora em uma {@link OperacaoFinanciada} da sua carteira (Sprint 29 — Epic
 * 15). Registrado por financeiro/admin e movimentado no escrow via provider fake — nenhum dinheiro
 * real e movido nesta fase (ativacao Celcoin/BaaS fica para a Fase 5).
 *
 * <p>Nasce em {@link StatusAporteCredora#PENDENTE} apos o registro local, ANTES de qualquer chamada
 * ao escrow (padrao anti-orphan da Sprint 20). Idempotencia por {@code UNIQUE (operacao_id,
 * idempotency_key)} (V54). {@code referenciaEscrow} e a referencia interna da movimentacao no
 * escrow — nunca exposta em contrato publico. {@code motivoFalhaSanitizado} guarda somente motivo
 * tratado; erro bruto de provider nao e persistido.
 */
@Entity
@Table(name = "aporte_credora")
public class AporteCredora extends EntidadeAuditavel {

    static final String MOEDA_PADRAO = "BRL";

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "operacao_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID operacaoId;

    @Column(name = "empresa_credora_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID empresaCredoraId;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal valor;

    @Column(name = "moeda", nullable = false, length = 3, updatable = false)
    private String moeda;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusAporteCredora status;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "referencia_escrow", length = 100)
    private String referenciaEscrow;

    @Column(name = "motivo_falha_sanitizado", length = 255)
    private String motivoFalhaSanitizado;

    protected AporteCredora() {
        // requerido pelo Hibernate
    }

    private AporteCredora(UUID id, UUID operacaoId, UUID empresaCredoraId, BigDecimal valor, String idempotencyKey) {
        this.id = id;
        this.operacaoId = operacaoId;
        this.empresaCredoraId = empresaCredoraId;
        this.valor = valor;
        this.moeda = MOEDA_PADRAO;
        this.status = StatusAporteCredora.PENDENTE;
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * Registra o aporte local em {@link StatusAporteCredora#PENDENTE}, antes de qualquer chamada ao
     * escrow.
     */
    public static AporteCredora registrar(
            UUID operacaoId, UUID empresaCredoraId, BigDecimal valor, String idempotencyKey) {
        Objects.requireNonNull(operacaoId, "operacaoId obrigatorio");
        Objects.requireNonNull(empresaCredoraId, "empresaCredoraId obrigatorio");
        BigDecimal valorNormalizado = validarValor(valor);
        validarIdempotencyKey(idempotencyKey);
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new AporteCredora(id, operacaoId, empresaCredoraId, valorNormalizado, idempotencyKey);
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

    /**
     * Registra que o escrow aceitou o aporte, guardando a referencia interna da movimentacao e
     * avancando para {@link StatusAporteCredora#EM_PROCESSAMENTO}.
     */
    public void marcarEmProcessamento(String referenciaEscrow) {
        exigirEstado(StatusAporteCredora.PENDENTE);
        Objects.requireNonNull(referenciaEscrow, "referenciaEscrow obrigatoria ao processar");
        if (referenciaEscrow.isBlank()) {
            throw new IllegalArgumentException("referenciaEscrow nao pode ser vazia");
        }
        this.referenciaEscrow = referenciaEscrow;
        this.status = StatusAporteCredora.EM_PROCESSAMENTO;
    }

    /** Liquida o aporte apos confirmacao da reconciliacao. Estado terminal. */
    public void marcarLiquidado() {
        exigirEstado(StatusAporteCredora.EM_PROCESSAMENTO);
        this.status = StatusAporteCredora.LIQUIDADO;
    }

    /**
     * Marca falha terminal com motivo ja sanitizado (nunca erro bruto de provider). Permitida a
     * partir de {@link StatusAporteCredora#PENDENTE} (registro recusado) e de {@link
     * StatusAporteCredora#EM_PROCESSAMENTO} (falha na liquidacao).
     */
    public void marcarFalhou(String motivoSanitizado) {
        exigirEstado(StatusAporteCredora.PENDENTE, StatusAporteCredora.EM_PROCESSAMENTO);
        Objects.requireNonNull(motivoSanitizado, "motivo sanitizado obrigatorio na falha");
        if (motivoSanitizado.isBlank()) {
            throw new IllegalArgumentException("motivo sanitizado nao pode ser vazio");
        }
        this.motivoFalhaSanitizado = motivoSanitizado;
        this.status = StatusAporteCredora.FALHOU;
    }

    private void exigirEstado(StatusAporteCredora... permitidos) {
        for (StatusAporteCredora permitido : permitidos) {
            if (this.status == permitido) {
                return;
            }
        }
        // Mensagem carrega apenas o status atual — sem id, chave ou referencia escrow.
        throw new IllegalStateException("transicao invalida a partir de " + this.status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOperacaoId() {
        return operacaoId;
    }

    public UUID getEmpresaCredoraId() {
        return empresaCredoraId;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getMoeda() {
        return moeda;
    }

    public StatusAporteCredora getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getReferenciaEscrow() {
        return referenciaEscrow;
    }

    public String getMotivoFalhaSanitizado() {
        return motivoFalhaSanitizado;
    }
}
