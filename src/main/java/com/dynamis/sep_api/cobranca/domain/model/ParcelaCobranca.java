package com.dynamis.sep_api.cobranca.domain.model;

import com.dynamis.sep_api.cobranca.domain.exception.ParcelaEstadoInvalidoException;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Parcela individual dentro de uma {@link AgendaPagamento}. Mantem composicao original (principal,
 * juros remuneratorios, multa, encargos) e historico de {@link Recebimento}s.
 *
 * <p>Sprint 12: composicao original e fixada na criacao. Atualizacao de multa/juros de mora ocorre
 * em tempo de leitura via {@code CalcularValorAtualizadoParcelaUseCase} (Task 12.5), sem mutar a
 * composicao persistida.
 */
@Entity
@Table(name = "parcela_cobranca")
public class ParcelaCobranca {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "agenda_id", nullable = false, updatable = false)
    private AgendaPagamento agenda;

    @Column(name = "numero", nullable = false, updatable = false)
    private int numero;

    @Column(name = "principal", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal principal;

    @Column(name = "juros", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal juros;

    @Column(name = "multa", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal multa;

    @Column(name = "encargos", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal encargos;

    @Column(name = "data_vencimento", nullable = false, updatable = false)
    private LocalDate dataVencimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusParcela status;

    @OneToMany(mappedBy = "parcela", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("dataRecebimento ASC")
    private List<Recebimento> recebimentos = new ArrayList<>();

    protected ParcelaCobranca() {}

    private ParcelaCobranca(
            UUID id, AgendaPagamento agenda, int numero, ComposicaoValor composicao, LocalDate dataVencimento) {
        this.id = id;
        this.agenda = agenda;
        this.numero = numero;
        this.principal = composicao.principal();
        this.juros = composicao.juros();
        this.multa = composicao.multa();
        this.encargos = composicao.encargos();
        this.dataVencimento = dataVencimento;
        this.status = StatusParcela.PENDENTE;
    }

    static ParcelaCobranca criar(
            AgendaPagamento agenda, int numero, ComposicaoValor composicao, LocalDate dataVencimento) {
        Objects.requireNonNull(agenda, "agenda obrigatoria");
        Objects.requireNonNull(composicao, "composicao obrigatoria");
        Objects.requireNonNull(dataVencimento, "dataVencimento obrigatoria");
        if (numero <= 0) {
            throw new IllegalArgumentException("numero deve ser positivo");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ParcelaCobranca(id, agenda, numero, composicao, dataVencimento);
    }

    /**
     * Registra um recebimento e ajusta o status comparando o total recebido contra o {@code
     * valorDevidoAtualizado} fornecido pelo use case (Task 12.5 calcula esse valor incluindo juros
     * de mora e multa quando ha atraso). Decisao deliberada: o agregado nao recalcula mora
     * sozinho — fronteira de calculo fica no use case com {@code Clock} injetado.
     */
    public Recebimento registrarRecebimento(
            BigDecimal valorRecebido,
            BigDecimal valorDevidoAtualizado,
            OffsetDateTime dataRecebimento,
            String meioPagamento,
            String identificadorExterno,
            String idempotencyKey,
            String observacao,
            UUID registradoPor) {
        if (!status.permiteRecebimento()) {
            throw new ParcelaEstadoInvalidoException("registrarRecebimento", status);
        }
        Objects.requireNonNull(valorDevidoAtualizado, "valorDevidoAtualizado obrigatorio");
        if (valorDevidoAtualizado.signum() <= 0) {
            throw new IllegalArgumentException("valorDevidoAtualizado deve ser positivo");
        }
        Recebimento recebimento = Recebimento.criar(
                this,
                valorRecebido,
                dataRecebimento,
                meioPagamento,
                identificadorExterno,
                idempotencyKey,
                observacao,
                registradoPor);
        recebimentos.add(recebimento);
        recalcularStatusAposRecebimento(valorDevidoAtualizado);
        return recebimento;
    }

    public void marcarAtrasada() {
        if (!status.permiteMarcarAtrasada()) {
            throw new ParcelaEstadoInvalidoException("marcarAtrasada", status);
        }
        this.status = StatusParcela.ATRASADA;
    }

    public ComposicaoValor composicaoOriginal() {
        return new ComposicaoValor(principal, juros, multa, encargos);
    }

    public BigDecimal valorTotal() {
        return composicaoOriginal().total();
    }

    public BigDecimal totalRecebido() {
        return recebimentos.stream()
                .map(Recebimento::getValorRecebido)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Optional<Recebimento> recebimentoPorIdempotencyKey(String idempotencyKey) {
        return recebimentos.stream()
                .filter(r -> r.getIdempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    private void recalcularStatusAposRecebimento(BigDecimal valorDevidoAtualizado) {
        BigDecimal recebido = totalRecebido();
        if (recebido.compareTo(valorDevidoAtualizado) >= 0) {
            this.status = StatusParcela.PAGA;
        } else if (recebido.signum() > 0) {
            this.status = StatusParcela.PARCIALMENTE_PAGA;
        }
    }

    public UUID getId() {
        return id;
    }

    public AgendaPagamento getAgenda() {
        return agenda;
    }

    public int getNumero() {
        return numero;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getJuros() {
        return juros;
    }

    public BigDecimal getMulta() {
        return multa;
    }

    public BigDecimal getEncargos() {
        return encargos;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public StatusParcela getStatus() {
        return status;
    }

    public List<Recebimento> getRecebimentos() {
        return Collections.unmodifiableList(recebimentos);
    }
}
