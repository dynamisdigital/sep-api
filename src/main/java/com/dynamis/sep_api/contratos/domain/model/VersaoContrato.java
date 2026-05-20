package com.dynamis.sep_api.contratos.domain.model;

import com.fasterxml.uuid.Generators;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Versao imutavel do conteudo de um {@link Contrato}. Cada geracao (inicial ou re-geracao
 * pre-aceite) cria nova {@code VersaoContrato} com {@code numero} incremental dentro do contrato.
 *
 * <p>Conteudo textual + hash SHA-256 servem como prova de integridade. Conteudo NUNCA deve ser
 * atualizado depois de persistido.
 */
@Entity
@Table(name = "versao_contrato")
public class VersaoContrato {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id", nullable = false, updatable = false)
    private Contrato contrato;

    @Column(name = "numero", nullable = false, updatable = false)
    private int numero;

    @Column(name = "conteudo_texto", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String conteudoTexto;

    @Column(name = "hash_sha256", nullable = false, length = 64, updatable = false)
    private String hashSha256;

    @Column(name = "data_geracao", nullable = false, updatable = false)
    private OffsetDateTime dataGeracao;

    @OneToMany(mappedBy = "versao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC")
    private List<ClausulaContratual> clausulas = new ArrayList<>();

    protected VersaoContrato() {}

    private VersaoContrato(
            UUID id,
            Contrato contrato,
            int numero,
            String conteudoTexto,
            String hashSha256,
            OffsetDateTime dataGeracao) {
        this.id = id;
        this.contrato = contrato;
        this.numero = numero;
        this.conteudoTexto = conteudoTexto;
        this.hashSha256 = hashSha256;
        this.dataGeracao = dataGeracao;
    }

    public static VersaoContrato criar(Contrato contrato, int numero, String conteudoTexto, String hashSha256) {
        Objects.requireNonNull(contrato, "contrato obrigatorio");
        if (numero <= 0) {
            throw new IllegalArgumentException("numero deve ser positivo");
        }
        Objects.requireNonNull(conteudoTexto, "conteudoTexto obrigatorio");
        if (conteudoTexto.isBlank()) {
            throw new IllegalArgumentException("conteudoTexto nao pode ser vazio");
        }
        Objects.requireNonNull(hashSha256, "hashSha256 obrigatorio");
        if (hashSha256.length() != 64) {
            throw new IllegalArgumentException("hashSha256 deve ter 64 chars hex");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new VersaoContrato(id, contrato, numero, conteudoTexto, hashSha256, OffsetDateTime.now());
    }

    public void adicionarClausula(int ordem, String titulo, String texto) {
        clausulas.add(ClausulaContratual.criar(this, ordem, titulo, texto));
    }

    public UUID getId() {
        return id;
    }

    public Contrato getContrato() {
        return contrato;
    }

    public int getNumero() {
        return numero;
    }

    public String getConteudoTexto() {
        return conteudoTexto;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public OffsetDateTime getDataGeracao() {
        return dataGeracao;
    }

    public List<ClausulaContratual> getClausulas() {
        return Collections.unmodifiableList(clausulas);
    }
}
