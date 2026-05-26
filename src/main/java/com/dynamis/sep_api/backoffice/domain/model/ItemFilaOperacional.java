package com.dynamis.sep_api.backoffice.domain.model;

import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
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
 * Item normalizado da fila operacional do backoffice (Sprint 14 Task 14.1).
 *
 * <p>Cada item agrega uma situacao que exige intervencao humana. A referencia para o objeto
 * original e logica (par {@code tipoEntidade}/{@code entidadeId}) — sem FK fisica, pra evitar
 * acoplamento ao schema dos modulos consumidos.
 *
 * <p>Idempotencia da fila e garantida via UNIQUE parcial em ({@code tipo}, {@code tipoEntidade},
 * {@code entidadeId}) restrita a itens ativos ({@code ABERTO}/{@code EM_TRATAMENTO}); apos
 * resolucao/ignorar, um novo item pode ser aberto pra mesma entidade.
 */
@Entity
@Table(name = "item_fila_operacional")
public class ItemFilaOperacional extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, updatable = false)
    private TipoItemFila tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false, length = 20)
    private PrioridadeItem prioridade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusItemFila status;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_entidade", nullable = false, length = 40, updatable = false)
    private TipoEntidadeReferenciada tipoEntidade;

    @Column(name = "entidade_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID entidadeId;

    @Column(name = "titulo", nullable = false, length = 255, updatable = false)
    private String titulo;

    @Column(name = "descricao", length = 4000, updatable = false)
    private String descricao;

    @Column(name = "atribuido_a", columnDefinition = "uuid")
    private UUID atribuidoA;

    @Column(name = "data_abertura", nullable = false, updatable = false)
    private OffsetDateTime dataAbertura;

    @Column(name = "data_resolucao")
    private OffsetDateTime dataResolucao;

    protected ItemFilaOperacional() {}

    private ItemFilaOperacional(
            UUID id,
            TipoItemFila tipo,
            PrioridadeItem prioridade,
            TipoEntidadeReferenciada tipoEntidade,
            UUID entidadeId,
            String titulo,
            String descricao,
            OffsetDateTime dataAbertura) {
        this.id = id;
        this.tipo = tipo;
        this.prioridade = prioridade;
        this.status = StatusItemFila.ABERTO;
        this.tipoEntidade = tipoEntidade;
        this.entidadeId = entidadeId;
        this.titulo = titulo;
        this.descricao = descricao;
        this.dataAbertura = dataAbertura;
    }

    public static ItemFilaOperacional abrir(
            TipoItemFila tipo,
            PrioridadeItem prioridade,
            TipoEntidadeReferenciada tipoEntidade,
            UUID entidadeId,
            String titulo,
            String descricao,
            OffsetDateTime dataAbertura) {
        Objects.requireNonNull(tipo, "tipo obrigatorio");
        Objects.requireNonNull(prioridade, "prioridade obrigatoria");
        Objects.requireNonNull(tipoEntidade, "tipoEntidade obrigatorio");
        Objects.requireNonNull(entidadeId, "entidadeId obrigatorio");
        exigirNaoVazio(titulo, "titulo");
        if (titulo.length() > 255) {
            throw new IllegalArgumentException("titulo nao pode exceder 255 caracteres");
        }
        Objects.requireNonNull(dataAbertura, "dataAbertura obrigatoria");
        return new ItemFilaOperacional(
                Generators.timeBasedReorderedGenerator().generate(),
                tipo,
                prioridade,
                tipoEntidade,
                entidadeId,
                titulo,
                descricao,
                dataAbertura);
    }

    public void assumir(UUID operadorId) {
        Objects.requireNonNull(operadorId, "operadorId obrigatorio");
        exigirTransicao(StatusItemFila.EM_TRATAMENTO, this.status::permiteAssumir);
        this.atribuidoA = operadorId;
        this.status = StatusItemFila.EM_TRATAMENTO;
    }

    public void resolver(OffsetDateTime resolvidoEm) {
        Objects.requireNonNull(resolvidoEm, "resolvidoEm obrigatorio");
        exigirTransicao(StatusItemFila.RESOLVIDO, this.status::permiteResolver);
        this.status = StatusItemFila.RESOLVIDO;
        this.dataResolucao = resolvidoEm;
    }

    public void ignorar(OffsetDateTime ignoradoEm) {
        Objects.requireNonNull(ignoradoEm, "ignoradoEm obrigatorio");
        exigirTransicao(StatusItemFila.IGNORADO, this.status::permiteIgnorar);
        this.status = StatusItemFila.IGNORADO;
        this.dataResolucao = ignoradoEm;
    }

    private void exigirTransicao(StatusItemFila alvo, java.util.function.BooleanSupplier permitido) {
        if (!permitido.getAsBoolean()) {
            throw new IllegalStateException("transicao invalida: " + this.status + " -> " + alvo);
        }
    }

    private static void exigirNaoVazio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " obrigatorio");
        }
    }

    public UUID getId() {
        return id;
    }

    public TipoItemFila getTipo() {
        return tipo;
    }

    public PrioridadeItem getPrioridade() {
        return prioridade;
    }

    public StatusItemFila getStatus() {
        return status;
    }

    public TipoEntidadeReferenciada getTipoEntidade() {
        return tipoEntidade;
    }

    public UUID getEntidadeId() {
        return entidadeId;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public UUID getAtribuidoA() {
        return atribuidoA;
    }

    public OffsetDateTime getDataAbertura() {
        return dataAbertura;
    }

    public OffsetDateTime getDataResolucao() {
        return dataResolucao;
    }
}
