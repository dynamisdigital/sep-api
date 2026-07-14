package com.dynamis.sep_api.pix.domain.model;

import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
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
 * Chave Pix da conta operacional/escrow (Sprint 31, Epic 15). Gestao assistida pelo financeiro:
 * cadastro via provider e remocao logica {@code ATIVA -> INATIVA} — o historico nunca e apagado.
 *
 * <p>Minimizacao (CMN 4.656/2018 + LGPD): o valor da chave <strong>nunca</strong> e persistido em
 * claro; a entidade guarda apenas {@code valorHash} (SHA-256, consistencia idempotente) e {@code
 * valorMascarado} (leitura). O {@code providerKeyId} e identificador tecnico interno e nao aparece
 * em resposta, erro ou auditoria.
 */
@Entity
@Table(name = "chave_pix")
public class ChavePix extends EntidadeAuditavel {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "conta_escrow_id", nullable = false)
    private UUID contaEscrowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40)
    private TipoChavePix tipo;

    @Column(name = "valor_hash", nullable = false, length = 64)
    private String valorHash;

    @Column(name = "valor_mascarado", nullable = false, length = 80)
    private String valorMascarado;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusChavePix status;

    @Column(name = "provider_key_id", nullable = false, length = 100)
    private String providerKeyId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "criada_por_usuario_id", nullable = false)
    private UUID criadaPorUsuarioId;

    @Column(name = "removida_por_usuario_id")
    private UUID removidaPorUsuarioId;

    @Column(name = "criada_em", nullable = false)
    private OffsetDateTime criadaEm;

    @Column(name = "removida_em")
    private OffsetDateTime removidaEm;

    protected ChavePix() {
        // JPA
    }

    /** Cadastra uma chave {@link StatusChavePix#ATIVA} ja minimizada (hash + mascara, nunca valor bruto). */
    public static ChavePix cadastrar(
            UUID contaEscrowId,
            TipoChavePix tipo,
            String valorHash,
            String valorMascarado,
            String providerKeyId,
            String idempotencyKey,
            UUID criadaPorUsuarioId,
            OffsetDateTime criadaEm) {
        Objects.requireNonNull(contaEscrowId, "contaEscrowId obrigatorio");
        Objects.requireNonNull(tipo, "tipo obrigatorio");
        Objects.requireNonNull(criadaPorUsuarioId, "criadaPorUsuarioId obrigatorio");
        Objects.requireNonNull(criadaEm, "criadaEm obrigatorio");
        validarTexto(valorHash, 64, 64, "valorHash");
        validarTexto(valorMascarado, 1, 80, "valorMascarado");
        validarTexto(providerKeyId, 1, 100, "providerKeyId");
        validarTexto(idempotencyKey, 1, 100, "idempotencyKey");

        ChavePix chave = new ChavePix();
        chave.id = Generators.timeBasedReorderedGenerator().generate();
        chave.contaEscrowId = contaEscrowId;
        chave.tipo = tipo;
        chave.valorHash = valorHash;
        chave.valorMascarado = valorMascarado;
        chave.status = StatusChavePix.ATIVA;
        chave.providerKeyId = providerKeyId;
        chave.idempotencyKey = idempotencyKey;
        chave.criadaPorUsuarioId = criadaPorUsuarioId;
        chave.criadaEm = criadaEm;
        return chave;
    }

    private static void validarTexto(String valor, int minimo, int maximo, String campo) {
        Objects.requireNonNull(valor, campo + " obrigatorio");
        String semEspacos = valor.strip();
        if (semEspacos.length() < minimo || semEspacos.length() > maximo) {
            throw new IllegalArgumentException(campo + " deve ter entre " + minimo + " e " + maximo + " caracteres");
        }
    }

    /**
     * Transiciona {@code ATIVA -> INATIVA} registrando ator e instante. Chave ja {@link
     * StatusChavePix#INATIVA} e no-op idempotente: retorna {@code false} e preserva o registro da
     * primeira remocao.
     */
    public boolean inativar(UUID usuarioId, OffsetDateTime instante) {
        Objects.requireNonNull(usuarioId, "usuarioId obrigatorio");
        Objects.requireNonNull(instante, "instante obrigatorio");
        if (status == StatusChavePix.INATIVA) {
            return false;
        }
        this.status = StatusChavePix.INATIVA;
        this.removidaPorUsuarioId = usuarioId;
        this.removidaEm = instante;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContaEscrowId() {
        return contaEscrowId;
    }

    public TipoChavePix getTipo() {
        return tipo;
    }

    public String getValorHash() {
        return valorHash;
    }

    public String getValorMascarado() {
        return valorMascarado;
    }

    public StatusChavePix getStatus() {
        return status;
    }

    public String getProviderKeyId() {
        return providerKeyId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getCriadaPorUsuarioId() {
        return criadaPorUsuarioId;
    }

    public UUID getRemovidaPorUsuarioId() {
        return removidaPorUsuarioId;
    }

    public OffsetDateTime getCriadaEm() {
        return criadaEm;
    }

    public OffsetDateTime getRemovidaEm() {
        return removidaEm;
    }
}
