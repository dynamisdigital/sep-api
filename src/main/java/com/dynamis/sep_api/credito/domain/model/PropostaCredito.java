package com.dynamis.sep_api.credito.domain.model;

import com.dynamis.sep_api.credito.domain.exception.PropostaInvalidaException;
import com.dynamis.sep_api.credito.domain.exception.StatusPropostaInvalidoException;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Agregado raiz do modulo {@code credito} (Sprint 8). Representa uma solicitacao de credito feita
 * por um {@code tomador} (PF ou PJ) com onboarding {@code APROVADO_FINAL} (Sprint 7).
 *
 * <p>UUID v6 gerado via {@code timeBasedReorderedGenerator()}. Construtor publico {@code
 * protected} pra Hibernate; entidades novas devem usar {@link #criar(UUID, UUID, TipoOperacao,
 * Money, int)}.
 *
 * <p>Maquina de estados em {@link StatusProposta}:
 *
 * <pre>
 *   EM_ANALISE  -> PRE_APROVADA | REJEITADA | PENDENCIA
 *   PRE_APROVADA -> APROVADA | REJEITADA | PENDENCIA
 *   PENDENCIA   -> APROVADA | REJEITADA | EM_ANALISE
 *   APROVADA / REJEITADA = finais (nenhuma transicao saindo)
 * </pre>
 *
 * <p>{@link #aplicarSugestaoMotor(StatusProposta)} transiciona a partir de {@code EM_ANALISE} com
 * o status sugerido pelo motor de regras (Task 8.2/8.3). {@link
 * #registrarDecisaoManual(DecisaoParecer)} aplica a decisao final de operador financeiro (Task
 * 8.4) — rejeita transicao em estados finais ou origem invalida.
 */
@Entity
@Table(name = "proposta_credito")
public class PropostaCredito extends EntidadeAuditavel {

    private static final Set<StatusProposta> ORIGENS_PARECER_FINAL =
            Set.of(StatusProposta.EM_ANALISE, StatusProposta.PRE_APROVADA, StatusProposta.PENDENCIA);

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tomador_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID tomadorId;

    @Column(name = "solicitacao_onboarding_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID solicitacaoOnboardingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacao", nullable = false, length = 40, updatable = false)
    private TipoOperacao tipoOperacao;

    @Column(name = "valor_solicitado", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal valorSolicitado;

    @Column(name = "moeda", nullable = false, length = 3, updatable = false)
    private String moeda;

    @Column(name = "prazo_meses", nullable = false, updatable = false)
    private int prazoMeses;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusProposta status;

    /**
     * Taxa de juros remuneratorios mensal aprovada (decimal — 0.02 = 2%). Sprint 12 Task 12.3
     * adiciona via V26 como NULL pra registros legados; sprint posterior popula explicitamente.
     */
    @Column(name = "taxa_juros_mensal", precision = 8, scale = 6)
    private BigDecimal taxaJurosMensal;

    protected PropostaCredito() {
        // requerido pelo Hibernate
    }

    private PropostaCredito(
            UUID id,
            UUID tomadorId,
            UUID solicitacaoOnboardingId,
            TipoOperacao tipoOperacao,
            Money valorSolicitado,
            int prazoMeses) {
        this.id = id;
        this.tomadorId = tomadorId;
        this.solicitacaoOnboardingId = solicitacaoOnboardingId;
        this.tipoOperacao = tipoOperacao;
        this.valorSolicitado = valorSolicitado.valor();
        this.moeda = valorSolicitado.moeda();
        this.prazoMeses = prazoMeses;
        this.status = StatusProposta.EM_ANALISE;
    }

    /**
     * Cria uma nova proposta em {@link StatusProposta#EM_ANALISE}. Os ids referenciam tomador (PF
     * ou PJ via {@code usuario.id}) e solicitacao de onboarding (que deve estar {@code
     * APROVADO_FINAL}; verificado no use case).
     */
    public static PropostaCredito criar(
            UUID tomadorId,
            UUID solicitacaoOnboardingId,
            TipoOperacao tipoOperacao,
            Money valorSolicitado,
            int prazoMeses) {
        Objects.requireNonNull(tomadorId, "tomadorId obrigatorio");
        Objects.requireNonNull(solicitacaoOnboardingId, "solicitacaoOnboardingId obrigatorio");
        Objects.requireNonNull(tipoOperacao, "tipoOperacao obrigatorio");
        Objects.requireNonNull(valorSolicitado, "valorSolicitado obrigatorio");
        if (prazoMeses <= 0) {
            throw new PropostaInvalidaException("prazoMeses deve ser positivo");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new PropostaCredito(id, tomadorId, solicitacaoOnboardingId, tipoOperacao, valorSolicitado, prazoMeses);
    }

    /**
     * Aplica o status sugerido pelo motor de regras (Task 8.2/8.3). Aceita apenas a partir de
     * {@code EM_ANALISE} e somente {@code PRE_APROVADA}, {@code EM_ANALISE} (mantem) ou {@code
     * REJEITADA}. Demais status devem ser obtidos via {@link #registrarDecisaoManual(DecisaoParecer)}.
     */
    public void aplicarSugestaoMotor(StatusProposta sugerido) {
        Objects.requireNonNull(sugerido, "status sugerido obrigatorio");
        if (status != StatusProposta.EM_ANALISE) {
            throw new StatusPropostaInvalidoException("aplicarSugestaoMotor", status, sugerido);
        }
        if (sugerido != StatusProposta.PRE_APROVADA
                && sugerido != StatusProposta.EM_ANALISE
                && sugerido != StatusProposta.REJEITADA) {
            throw new StatusPropostaInvalidoException("aplicarSugestaoMotor", status, sugerido);
        }
        this.status = sugerido;
    }

    /**
     * Move proposta para {@code PENDENCIA} (ex.: motor sem auditoria disponivel, regra retorna
     * pendente bloqueante). Aceita a partir de {@code EM_ANALISE} ou {@code PRE_APROVADA}.
     */
    public void marcarPendencia() {
        if (status.isFinal() || status == StatusProposta.PENDENCIA) {
            throw new StatusPropostaInvalidoException("marcarPendencia", status, StatusProposta.PENDENCIA);
        }
        this.status = StatusProposta.PENDENCIA;
    }

    /**
     * Aplica decisao manual do operador financeiro (Task 8.4). Sobrepoe a sugestao do motor.
     *
     * <ul>
     *   <li>{@code APROVAR} -> {@link StatusProposta#APROVADA} (final)
     *   <li>{@code REJEITAR} -> {@link StatusProposta#REJEITADA} (final)
     *   <li>{@code PENDENCIA} -> {@link StatusProposta#PENDENCIA}
     * </ul>
     *
     * <p>Lanca {@link StatusPropostaInvalidoException} se a proposta ja estiver em estado final.
     */
    public void registrarDecisaoManual(DecisaoParecer decisao) {
        Objects.requireNonNull(decisao, "decisao obrigatoria");
        if (decisao == DecisaoParecer.APROVAR || decisao == DecisaoParecer.REJEITAR) {
            if (!ORIGENS_PARECER_FINAL.contains(status)) {
                throw new StatusPropostaInvalidoException(
                        "registrarDecisaoManual",
                        status,
                        decisao == DecisaoParecer.APROVAR ? StatusProposta.APROVADA : StatusProposta.REJEITADA);
            }
            this.status = decisao == DecisaoParecer.APROVAR ? StatusProposta.APROVADA : StatusProposta.REJEITADA;
            return;
        }
        // PENDENCIA
        if (status.isFinal()) {
            throw new StatusPropostaInvalidoException("registrarDecisaoManual", status, StatusProposta.PENDENCIA);
        }
        this.status = StatusProposta.PENDENCIA;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTomadorId() {
        return tomadorId;
    }

    public UUID getSolicitacaoOnboardingId() {
        return solicitacaoOnboardingId;
    }

    public TipoOperacao getTipoOperacao() {
        return tipoOperacao;
    }

    public BigDecimal getValorSolicitado() {
        return valorSolicitado;
    }

    public String getMoeda() {
        return moeda;
    }

    public int getPrazoMeses() {
        return prazoMeses;
    }

    public StatusProposta getStatus() {
        return status;
    }

    /**
     * Taxa de juros aprovada — {@code Optional.empty()} para registros legados ou propostas ainda
     * sem decisao com taxa estruturada. Consumido por {@code cobranca} (Sprint 12 Task 12.3) pra
     * gerar agenda; quando ausente, cobranca aplica fallback de config com log warn.
     */
    public Optional<BigDecimal> getTaxaJurosMensal() {
        return Optional.ofNullable(taxaJurosMensal);
    }
}
