package com.dynamis.sep_api.cobranca.domain.model;

import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Agregado raiz do modulo {@code cobranca} (Sprint 12 Task 12.1). Representa a agenda de pagamento
 * de um {@code Contrato} ASSINADO, contendo todas as parcelas planejadas. Relacao 1:1 com
 * contrato.
 *
 * <p>Nasce com todas as parcelas. Status segue {@code StatusParcela} dentro de cada parcela; a
 * agenda em si nao tem status agregado nesta sprint.
 */
@Entity
@Table(name = "agenda_pagamento")
public class AgendaPagamento extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "contrato_id", columnDefinition = "uuid", nullable = false, unique = true, updatable = false)
    private UUID contratoId;

    @Column(name = "numero_parcelas", nullable = false, updatable = false)
    private int numeroParcelas;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal valorTotal;

    @Column(name = "data_geracao", nullable = false, updatable = false)
    private OffsetDateTime dataGeracao;

    @OneToMany(mappedBy = "agenda", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("numero ASC")
    private List<ParcelaCobranca> parcelas = new ArrayList<>();

    protected AgendaPagamento() {}

    private AgendaPagamento(
            UUID id, UUID contratoId, int numeroParcelas, BigDecimal valorTotal, OffsetDateTime dataGeracao) {
        this.id = id;
        this.contratoId = contratoId;
        this.numeroParcelas = numeroParcelas;
        this.valorTotal = valorTotal;
        this.dataGeracao = dataGeracao;
    }

    public static AgendaPagamento criar(UUID contratoId, List<ParcelaPlanejada> planejadas) {
        Objects.requireNonNull(contratoId, "contratoId obrigatorio");
        Objects.requireNonNull(planejadas, "planejadas obrigatorio");
        if (planejadas.isEmpty()) {
            throw new IllegalArgumentException("agenda exige ao menos uma parcela");
        }
        validarNumerosUnicos(planejadas);

        UUID id = Generators.timeBasedReorderedGenerator().generate();
        BigDecimal total = planejadas.stream()
                .map(p -> p.composicao().total())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        AgendaPagamento agenda = new AgendaPagamento(id, contratoId, planejadas.size(), total, OffsetDateTime.now());
        for (ParcelaPlanejada planejada : planejadas) {
            agenda.parcelas.add(ParcelaCobranca.criar(
                    agenda, planejada.numero(), planejada.composicao(), planejada.dataVencimento()));
        }
        return agenda;
    }

    private static void validarNumerosUnicos(List<ParcelaPlanejada> planejadas) {
        Set<Integer> numeros = new HashSet<>();
        for (ParcelaPlanejada p : planejadas) {
            if (!numeros.add(p.numero())) {
                throw new IllegalArgumentException("numero de parcela duplicado: " + p.numero());
            }
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getContratoId() {
        return contratoId;
    }

    public int getNumeroParcelas() {
        return numeroParcelas;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public OffsetDateTime getDataGeracao() {
        return dataGeracao;
    }

    public List<ParcelaCobranca> getParcelas() {
        return Collections.unmodifiableList(parcelas);
    }

    /** Estrutura de planejamento usada por {@link #criar(UUID, List)}. */
    public record ParcelaPlanejada(int numero, ComposicaoValor composicao, LocalDate dataVencimento) {
        public ParcelaPlanejada {
            if (numero <= 0) {
                throw new IllegalArgumentException("numero deve ser positivo");
            }
            Objects.requireNonNull(composicao, "composicao obrigatoria");
            Objects.requireNonNull(dataVencimento, "dataVencimento obrigatoria");
        }
    }
}
