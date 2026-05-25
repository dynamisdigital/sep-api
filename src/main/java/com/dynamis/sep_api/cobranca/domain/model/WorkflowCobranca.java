package com.dynamis.sep_api.cobranca.domain.model;

import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Etapa configuravel do workflow de cobranca (Sprint 13 Task 13.2).
 *
 * <p>Cada row representa um marco temporal de atraso ({@code diaAtraso}) com lista de templates a
 * disparar e flags operacionais. Seed inicial vem do YAML em Task 13.4
 * ({@code WorkflowCobrancaProperties}) — persistir pra auditoria e edicao futura por backoffice.
 *
 * <p>Lista de templates persistida em CSV ({@code notificacoes_csv}) pra evitar tabela auxiliar de
 * coleção em sprint inaugural; resolver (Task 13.4) le e converte. Nomes de template sao curtos
 * (ex. {@code email-amigavel}, {@code sms-lembrete}).
 *
 * <p>Unique constraint {@code uq_workflow_nome_dia_ativo} (parcial WHERE ativo) garante uma unica
 * etapa ativa por (nome, diaAtraso).
 */
@Entity
@Table(name = "workflow_cobranca")
public class WorkflowCobranca extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nome", nullable = false, length = 40, updatable = false)
    private String nome;

    @Column(name = "dia_atraso", nullable = false, updatable = false)
    private int diaAtraso;

    @Column(name = "notificacoes_csv", nullable = false, length = 500)
    private String notificacoesCsv;

    @Column(name = "flag_contato_manual", nullable = false)
    private boolean flagContatoManual;

    @Column(name = "escalonar_backoffice", nullable = false)
    private boolean escalonarBackoffice;

    @Column(name = "marcar_inadimplente", nullable = false)
    private boolean marcarInadimplente;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    protected WorkflowCobranca() {}

    private WorkflowCobranca(
            UUID id,
            String nome,
            int diaAtraso,
            String notificacoesCsv,
            boolean flagContatoManual,
            boolean escalonarBackoffice,
            boolean marcarInadimplente,
            boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.diaAtraso = diaAtraso;
        this.notificacoesCsv = notificacoesCsv;
        this.flagContatoManual = flagContatoManual;
        this.escalonarBackoffice = escalonarBackoffice;
        this.marcarInadimplente = marcarInadimplente;
        this.ativo = ativo;
    }

    public static WorkflowCobranca criar(
            String nome,
            int diaAtraso,
            List<String> notificacoes,
            boolean flagContatoManual,
            boolean escalonarBackoffice,
            boolean marcarInadimplente) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome obrigatorio");
        }
        if (diaAtraso < 0) {
            throw new IllegalArgumentException("diaAtraso nao pode ser negativo");
        }
        Objects.requireNonNull(notificacoes, "notificacoes obrigatorio");
        String csv = String.join(",", notificacoes);
        return new WorkflowCobranca(
                Generators.timeBasedReorderedGenerator().generate(),
                nome,
                diaAtraso,
                csv,
                flagContatoManual,
                escalonarBackoffice,
                marcarInadimplente,
                true);
    }

    public void desativar() {
        this.ativo = false;
    }

    public List<String> getNotificacoes() {
        if (notificacoesCsv == null || notificacoesCsv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(notificacoesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public int getDiaAtraso() {
        return diaAtraso;
    }

    public boolean isFlagContatoManual() {
        return flagContatoManual;
    }

    public boolean isEscalonarBackoffice() {
        return escalonarBackoffice;
    }

    public boolean isMarcarInadimplente() {
        return marcarInadimplente;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
