package com.dynamis.sep_api.cobranca.domain.model;

import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.TipoEventoCobranca;
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
 * Trilha operacional de cobranca por parcela (Sprint 13 Task 13.2).
 *
 * <p>Cada acao tomada sobre a parcela atrasada/inadimplente gera um registro: notificacao
 * automatica disparada pelo workflow (Task 13.4), contato manual do financeiro (Task 13.7),
 * marcacao de inadimplencia (Task 13.5) e eventos de renegociacao (Task 13.6).
 *
 * <p>Idempotencia das notificacoes automaticas vive na unique constraint da tabela ({@code
 * uq_evento_notificacao_idempotencia}) — combinacao {@code (parcela_id, dias_atraso, canal,
 * template)} previne envio duplicado no mesmo dia.
 *
 * <p>Nao persiste corpo da mensagem nem dados pessoais — campos {@code template} e {@code canal}
 * sao suficientes pra reconstituir contexto sem violar LGPD.
 */
@Entity
@Table(name = "evento_cobranca")
public class EventoCobranca extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "parcela_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID parcelaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, updatable = false)
    private TipoEventoCobranca tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", length = 10, updatable = false)
    private CanalNotificacao canal;

    @Column(name = "template", length = 80, updatable = false)
    private String template;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    private StatusEventoCobranca status;

    @Column(name = "dias_atraso", updatable = false)
    private Integer diasAtraso;

    @Column(name = "descricao", length = 500, updatable = false)
    private String descricao;

    @Column(name = "registrado_por", columnDefinition = "uuid", updatable = false)
    private UUID registradoPor;

    @Column(name = "data_evento", nullable = false, updatable = false)
    private OffsetDateTime dataEvento;

    protected EventoCobranca() {}

    private EventoCobranca(
            UUID id,
            UUID parcelaId,
            TipoEventoCobranca tipo,
            CanalNotificacao canal,
            String template,
            StatusEventoCobranca status,
            Integer diasAtraso,
            String descricao,
            UUID registradoPor,
            OffsetDateTime dataEvento) {
        this.id = id;
        this.parcelaId = parcelaId;
        this.tipo = tipo;
        this.canal = canal;
        this.template = template;
        this.status = status;
        this.diasAtraso = diasAtraso;
        this.descricao = descricao;
        this.registradoPor = registradoPor;
        this.dataEvento = dataEvento;
    }

    /** Etapa do workflow automatico (Task 13.4). */
    public static EventoCobranca notificacaoAutomatica(
            UUID parcelaId,
            CanalNotificacao canal,
            String template,
            int diasAtraso,
            StatusEventoCobranca status,
            String descricaoTecnica,
            OffsetDateTime dataEvento) {
        Objects.requireNonNull(parcelaId, "parcelaId obrigatorio");
        Objects.requireNonNull(canal, "canal obrigatorio");
        exigirNaoVazio(template, "template");
        Objects.requireNonNull(status, "status obrigatorio");
        Objects.requireNonNull(dataEvento, "dataEvento obrigatoria");
        return new EventoCobranca(
                novoId(),
                parcelaId,
                TipoEventoCobranca.NOTIFICACAO_AUTOMATICA,
                canal,
                template,
                status,
                diasAtraso,
                descricaoTecnica,
                null,
                dataEvento);
    }

    /** Contato manual registrado pelo financeiro (Task 13.7). */
    public static EventoCobranca contatoManual(
            UUID parcelaId, UUID registradoPor, Integer diasAtraso, String descricao, OffsetDateTime dataEvento) {
        Objects.requireNonNull(parcelaId, "parcelaId obrigatorio");
        Objects.requireNonNull(registradoPor, "registradoPor obrigatorio");
        exigirNaoVazio(descricao, "descricao");
        Objects.requireNonNull(dataEvento, "dataEvento obrigatoria");
        return new EventoCobranca(
                novoId(),
                parcelaId,
                TipoEventoCobranca.CONTATO_MANUAL,
                null,
                null,
                StatusEventoCobranca.SUCESSO,
                diasAtraso,
                descricao,
                registradoPor,
                dataEvento);
    }

    /** Evento de mudanca de estado disparado por job/use case (renegociacao + inadimplencia). */
    public static EventoCobranca mudancaEstado(
            UUID parcelaId,
            TipoEventoCobranca tipo,
            Integer diasAtraso,
            String descricao,
            UUID registradoPor,
            OffsetDateTime dataEvento) {
        Objects.requireNonNull(parcelaId, "parcelaId obrigatorio");
        Objects.requireNonNull(tipo, "tipo obrigatorio");
        if (tipo == TipoEventoCobranca.NOTIFICACAO_AUTOMATICA || tipo == TipoEventoCobranca.CONTATO_MANUAL) {
            throw new IllegalArgumentException("tipo invalido para mudancaEstado: " + tipo);
        }
        Objects.requireNonNull(dataEvento, "dataEvento obrigatoria");
        return new EventoCobranca(
                novoId(),
                parcelaId,
                tipo,
                null,
                null,
                StatusEventoCobranca.SUCESSO,
                diasAtraso,
                descricao,
                registradoPor,
                dataEvento);
    }

    private static UUID novoId() {
        return Generators.timeBasedReorderedGenerator().generate();
    }

    private static void exigirNaoVazio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " obrigatorio");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getParcelaId() {
        return parcelaId;
    }

    public TipoEventoCobranca getTipo() {
        return tipo;
    }

    public CanalNotificacao getCanal() {
        return canal;
    }

    public String getTemplate() {
        return template;
    }

    public StatusEventoCobranca getStatus() {
        return status;
    }

    public Integer getDiasAtraso() {
        return diasAtraso;
    }

    public String getDescricao() {
        return descricao;
    }

    public UUID getRegistradoPor() {
        return registradoPor;
    }

    public OffsetDateTime getDataEvento() {
        return dataEvento;
    }
}
