package com.dynamis.sep_api.credito.domain.model;

import com.dynamis.sep_api.credito.domain.exception.ConsentimentoInvalidoException;
import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
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
 * Consentimento Open Finance Brasil (Sprint 9 — Epic 6 parte 2). Vincula uma proposta de credito a
 * um link de autorizacao Celcoin/Finansystech para coleta de movimentacao bancaria do tomador.
 *
 * <p>Opt-in obrigatorio: proposta pode ser aprovada sem consentimento, mas Open Finance enriquece
 * score interno via {@code RegraOpenFinanceMovimentacao}.
 *
 * <p>Maquina de estados em {@link StatusConsentimento}. Apenas {@link StatusConsentimento#AUTORIZADO}
 * habilita consulta via {@code OpenFinanceProvider}.
 */
@Entity
@Table(name = "consentimento_open_finance")
public class ConsentimentoOpenFinance {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "proposta_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID propostaId;

    @Column(name = "tomador_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID tomadorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusConsentimento status;

    @Column(name = "url_autorizacao", length = 1000)
    private String urlAutorizacao;

    @Column(name = "id_externo_celcoin", length = 255)
    private String idExternoCelcoin;

    @Column(name = "data_inicio", nullable = false)
    private OffsetDateTime dataInicio;

    @Column(name = "data_autorizacao")
    private OffsetDateTime dataAutorizacao;

    @Column(name = "data_expiracao")
    private OffsetDateTime dataExpiracao;

    protected ConsentimentoOpenFinance() {
        // Hibernate
    }

    private ConsentimentoOpenFinance(
            UUID id,
            UUID propostaId,
            UUID tomadorId,
            String urlAutorizacao,
            String idExternoCelcoin,
            OffsetDateTime dataExpiracao) {
        this.id = id;
        this.propostaId = propostaId;
        this.tomadorId = tomadorId;
        this.status = StatusConsentimento.PENDENTE;
        this.urlAutorizacao = urlAutorizacao;
        this.idExternoCelcoin = idExternoCelcoin;
        this.dataInicio = OffsetDateTime.now();
        this.dataExpiracao = dataExpiracao;
    }

    /**
     * Cria registro local PENDENTE sem dados do provider externo (Sprint 9 fix code review Task
     * 9.3 — anti-orphan). Use case deve persistir esta entidade ANTES de chamar
     * {@code OpenFinanceProvider}, usar {@link #getId()} como idempotency-key estavel, e depois
     * chamar {@link #vincularExterno(String, String, OffsetDateTime)} com a resposta. Se o
     * provider falhar, transacao rola para tras sem deixar consentimento local nem externo
     * orfao no provider (idempotency-key e estavel pra retry).
     */
    public static ConsentimentoOpenFinance iniciarLocal(UUID propostaId, UUID tomadorId) {
        Objects.requireNonNull(propostaId, "propostaId obrigatorio");
        Objects.requireNonNull(tomadorId, "tomadorId obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ConsentimentoOpenFinance(id, propostaId, tomadorId, null, null, null);
    }

    /**
     * Cria consentimento ja vinculado a provider (testes/legacy). Use case real deve usar
     * {@link #iniciarLocal(UUID, UUID)} + {@link #vincularExterno(String, String, OffsetDateTime)}.
     */
    public static ConsentimentoOpenFinance iniciar(
            UUID propostaId,
            UUID tomadorId,
            String urlAutorizacao,
            String idExternoCelcoin,
            OffsetDateTime dataExpiracao) {
        Objects.requireNonNull(propostaId, "propostaId obrigatorio");
        Objects.requireNonNull(tomadorId, "tomadorId obrigatorio");
        Objects.requireNonNull(urlAutorizacao, "urlAutorizacao obrigatorio");
        Objects.requireNonNull(idExternoCelcoin, "idExternoCelcoin obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ConsentimentoOpenFinance(id, propostaId, tomadorId, urlAutorizacao, idExternoCelcoin, dataExpiracao);
    }

    /**
     * Anexa dados do provider externo a um consentimento local. So aceita se ainda nao houver
     * vinculo (idExternoCelcoin null) e status seguir PENDENTE.
     */
    public void vincularExterno(String idExternoCelcoin, String urlAutorizacao, OffsetDateTime dataExpiracao) {
        Objects.requireNonNull(idExternoCelcoin, "idExternoCelcoin obrigatorio");
        Objects.requireNonNull(urlAutorizacao, "urlAutorizacao obrigatorio");
        if (this.idExternoCelcoin != null) {
            throw new ConsentimentoInvalidoException(
                    "vincularExterno chamado com idExterno ja definido: " + this.idExternoCelcoin);
        }
        if (status != StatusConsentimento.PENDENTE) {
            throw new ConsentimentoInvalidoException("vincularExterno", status, StatusConsentimento.PENDENTE);
        }
        this.idExternoCelcoin = idExternoCelcoin;
        this.urlAutorizacao = urlAutorizacao;
        this.dataExpiracao = dataExpiracao;
    }

    /** Marca como autorizado. Aceita apenas a partir de {@link StatusConsentimento#PENDENTE}. */
    public void autorizar() {
        if (status != StatusConsentimento.PENDENTE) {
            throw new ConsentimentoInvalidoException("autorizar", status, StatusConsentimento.AUTORIZADO);
        }
        this.status = StatusConsentimento.AUTORIZADO;
        this.dataAutorizacao = OffsetDateTime.now();
    }

    /** Marca como negado. Aceita apenas a partir de {@link StatusConsentimento#PENDENTE}. */
    public void negar() {
        if (status != StatusConsentimento.PENDENTE) {
            throw new ConsentimentoInvalidoException("negar", status, StatusConsentimento.NEGADO);
        }
        this.status = StatusConsentimento.NEGADO;
    }

    /**
     * Revoga consentimento previamente {@link StatusConsentimento#AUTORIZADO} (Sprint 15 — 15F-019).
     *
     * <p>Open Finance Brasil permite que o detentor revogue o consentimento via app do banco apos
     * ter autorizado; o provider notifica via callback NEGADO tardio. O provider e source-of-truth,
     * entao o agregado aceita a transicao AUTORIZADO -> NEGADO e preserva a {@code dataAutorizacao}
     * original como trilha de auditoria.
     *
     * <p>Estados {@code NEGADO} ou {@code EXPIRADO} ja sao terminais — rejeita transicao.
     */
    public void revogar() {
        if (status != StatusConsentimento.AUTORIZADO) {
            throw new ConsentimentoInvalidoException("revogar", status, StatusConsentimento.NEGADO);
        }
        this.status = StatusConsentimento.NEGADO;
    }

    /** Marca como expirado. So aceita se nao houver decisao final. */
    public void expirar() {
        if (status.isFinal()) {
            throw new ConsentimentoInvalidoException("expirar", status, StatusConsentimento.EXPIRADO);
        }
        this.status = StatusConsentimento.EXPIRADO;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropostaId() {
        return propostaId;
    }

    public UUID getTomadorId() {
        return tomadorId;
    }

    public StatusConsentimento getStatus() {
        return status;
    }

    public String getUrlAutorizacao() {
        return urlAutorizacao;
    }

    public String getIdExternoCelcoin() {
        return idExternoCelcoin;
    }

    public OffsetDateTime getDataInicio() {
        return dataInicio;
    }

    public OffsetDateTime getDataAutorizacao() {
        return dataAutorizacao;
    }

    public OffsetDateTime getDataExpiracao() {
        return dataExpiracao;
    }
}
