package com.dynamis.sep_api.credores.domain.model;

import com.dynamis.sep_api.credores.domain.vo.CriterioMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sugestao persistida de matching entre a {@link EmpresaCredora} dona e uma {@link
 * OperacaoFinanciada} da propria carteira pronta para receber aporte assistido (Sprint 30 — Epic
 * 15). O sistema sugere; a decisao e sempre de financeiro/admin com step-up estrito — nenhum
 * aporte, Pix ou associacao e disparado automaticamente pela confirmacao.
 *
 * <p>{@code criteriosSnapshot} congela os criterios de elegibilidade atendidos no momento da
 * sugestao (codigos de {@link CriterioMatchingCredoraOperacao} separados por {@code ;}) —
 * auditavel sem recalculo futuro e sem dado sensivel. {@code motivoDecisaoSanitizado} guarda
 * somente motivo tratado; texto bruto do operador e sanitizado na borda.
 */
@Entity
@Table(name = "matching_credora_operacao")
public class MatchingCredoraOperacao extends EntidadeAuditavel {

    static final int MOTIVO_MAX = 255;

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "empresa_credora_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID empresaCredoraId;

    @Column(name = "operacao_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID operacaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusMatchingCredoraOperacao status;

    @Column(name = "valor_elegivel", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal valorElegivel;

    @Column(name = "criterios_snapshot", nullable = false, length = 500, updatable = false)
    private String criteriosSnapshot;

    @Column(name = "decidido_por_usuario_id", columnDefinition = "uuid")
    private UUID decididoPorUsuarioId;

    @Column(name = "motivo_decisao_sanitizado", length = MOTIVO_MAX)
    private String motivoDecisaoSanitizado;

    @Column(name = "data_decisao")
    private OffsetDateTime dataDecisao;

    protected MatchingCredoraOperacao() {
        // requerido pelo Hibernate
    }

    private MatchingCredoraOperacao(
            UUID id, UUID empresaCredoraId, UUID operacaoId, BigDecimal valorElegivel, String criteriosSnapshot) {
        this.id = id;
        this.empresaCredoraId = empresaCredoraId;
        this.operacaoId = operacaoId;
        this.valorElegivel = valorElegivel;
        this.criteriosSnapshot = criteriosSnapshot;
        this.status = StatusMatchingCredoraOperacao.SUGERIDA;
    }

    /**
     * Cria a sugestao em {@link StatusMatchingCredoraOperacao#SUGERIDA} congelando os criterios
     * atendidos e o valor elegivel avaliados pela regra de elegibilidade (Task 30.1).
     */
    public static MatchingCredoraOperacao sugerir(
            UUID empresaCredoraId,
            UUID operacaoId,
            BigDecimal valorElegivel,
            List<CriterioMatchingCredoraOperacao> criteriosAtendidos) {
        Objects.requireNonNull(empresaCredoraId, "empresaCredoraId obrigatorio");
        Objects.requireNonNull(operacaoId, "operacaoId obrigatorio");
        BigDecimal valorNormalizado = validarValor(valorElegivel);
        Objects.requireNonNull(criteriosAtendidos, "criterios atendidos obrigatorios");
        if (criteriosAtendidos.isEmpty()) {
            throw new IllegalArgumentException("sugestao exige ao menos um criterio atendido");
        }
        String snapshot = criteriosAtendidos.stream()
                .map(CriterioMatchingCredoraOperacao::name)
                .collect(Collectors.joining(";"));
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new MatchingCredoraOperacao(id, empresaCredoraId, operacaoId, valorNormalizado, snapshot);
    }

    private static BigDecimal validarValor(BigDecimal valor) {
        Objects.requireNonNull(valor, "valorElegivel obrigatorio");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("valorElegivel deve ser positivo");
        }
        if (valor.scale() > 2) {
            throw new IllegalArgumentException("valorElegivel nao pode ter mais de 2 casas decimais");
        }
        return valor.setScale(2);
    }

    /** Decisao assistida: confirma a sugestao. Terminal — replay falha sem alterar estado. */
    public void confirmar(UUID decididoPorUsuarioId, String motivoSanitizado) {
        decidir(StatusMatchingCredoraOperacao.CONFIRMADA, decididoPorUsuarioId, motivoSanitizado);
    }

    /** Decisao assistida: rejeita a sugestao. Terminal — o par nao volta a ser sugerido. */
    public void rejeitar(UUID decididoPorUsuarioId, String motivoSanitizado) {
        decidir(StatusMatchingCredoraOperacao.REJEITADA, decididoPorUsuarioId, motivoSanitizado);
    }

    private void decidir(StatusMatchingCredoraOperacao novoStatus, UUID decididoPor, String motivoSanitizado) {
        if (this.status != StatusMatchingCredoraOperacao.SUGERIDA) {
            // Mensagem carrega apenas o status atual — sem id, par ou motivo.
            throw new IllegalStateException("transicao invalida a partir de " + this.status);
        }
        Objects.requireNonNull(decididoPor, "decididoPorUsuarioId obrigatorio");
        this.motivoDecisaoSanitizado = normalizarMotivo(motivoSanitizado);
        this.decididoPorUsuarioId = decididoPor;
        this.dataDecisao = OffsetDateTime.now();
        this.status = novoStatus;
    }

    private static String normalizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return null;
        }
        String normalizado = motivo.trim();
        if (normalizado.length() > MOTIVO_MAX) {
            throw new IllegalArgumentException("motivo nao pode exceder " + MOTIVO_MAX + " caracteres");
        }
        return normalizado;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmpresaCredoraId() {
        return empresaCredoraId;
    }

    public UUID getOperacaoId() {
        return operacaoId;
    }

    public StatusMatchingCredoraOperacao getStatus() {
        return status;
    }

    public BigDecimal getValorElegivel() {
        return valorElegivel;
    }

    public String getCriteriosSnapshot() {
        return criteriosSnapshot;
    }

    public UUID getDecididoPorUsuarioId() {
        return decididoPorUsuarioId;
    }

    public String getMotivoDecisaoSanitizado() {
        return motivoDecisaoSanitizado;
    }

    public OffsetDateTime getDataDecisao() {
        return dataDecisao;
    }
}
