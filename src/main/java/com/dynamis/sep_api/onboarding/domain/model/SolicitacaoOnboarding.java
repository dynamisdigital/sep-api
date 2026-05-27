package com.dynamis.sep_api.onboarding.domain.model;

import com.dynamis.sep_api.onboarding.domain.exception.StatusOnboardingInvalidoException;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Agregado raiz do modulo {@code onboarding}. Representa uma solicitacao de verificacao
 * cadastral — KYC PF (Sprint 6) ou KYB PJ (Sprint 7) — distinguidas pelo campo {@code tipo}.
 *
 * <p>UUID v6 gerado via {@code timeBasedReorderedGenerator()}. Construtor publico
 * {@code protected} pra Hibernate; entidades novas devem usar {@link #criarPessoa(UUID, Cpf,
 * String, LocalDate)} ou {@link #criarEmpresa(UUID, String, String)}.
 *
 * <p>Maquina de estados em {@link StatusOnboarding}.
 */
@Entity
@Table(name = "solicitacao_onboarding")
public class SolicitacaoOnboarding extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20, updatable = false)
    private TipoSolicitante tipo;

    @Column(name = "documento", nullable = false, length = 14, updatable = false)
    private String documento;

    @Column(name = "cpf", length = 11, updatable = false)
    private String cpf;

    @Column(name = "nome_completo", nullable = false, length = 255)
    private String nomeCompleto;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusOnboarding status;

    @Column(name = "id_verificacao_externa", length = 120)
    private String idVerificacaoExterna;

    @Column(name = "revisao_documentos", nullable = false)
    private int revisaoDocumentos;

    @Version
    @Column(name = "versao", nullable = false)
    private long versao;

    protected SolicitacaoOnboarding() {
        // requerido pelo Hibernate
    }

    private SolicitacaoOnboarding(
            UUID id,
            UUID usuarioId,
            TipoSolicitante tipo,
            String documento,
            String cpf,
            String nomeCompleto,
            LocalDate dataNascimento) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.documento = documento;
        this.cpf = cpf;
        this.nomeCompleto = nomeCompleto;
        this.dataNascimento = dataNascimento;
        this.status = StatusOnboarding.INICIADO;
        this.revisaoDocumentos = 0;
    }

    /** Factory PF: KYC pessoa fisica (Sprint 6). */
    public static SolicitacaoOnboarding criarPessoa(
            UUID usuarioId, Cpf cpf, String nomeCompleto, LocalDate dataNascimento) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new SolicitacaoOnboarding(
                id, usuarioId, TipoSolicitante.PESSOA, cpf.valor(), cpf.valor(), nomeCompleto, dataNascimento);
    }

    /**
     * Factory PJ: KYB empresa (Sprint 7). {@code cnpj} deve vir normalizado (14 digitos) — o VO
     * {@code Cnpj} sera plugado na Task 7.2; aqui aceitamos String pra desacoplar 7.1 de 7.2.
     */
    public static SolicitacaoOnboarding criarEmpresa(UUID usuarioId, String cnpj, String razaoSocial) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new SolicitacaoOnboarding(id, usuarioId, TipoSolicitante.EMPRESA, cnpj, null, razaoSocial, null);
    }

    /** Registra um novo documento; transiciona para {@code DOCUMENTOS_RECEBIDOS} se ainda nao estava. */
    public void registrarDocumentoEnviado() {
        if (status.isFinal()) {
            throw new StatusOnboardingInvalidoException("enviarDocumento", status);
        }
        if (status != StatusOnboarding.INICIADO && status != StatusOnboarding.DOCUMENTOS_RECEBIDOS) {
            throw new StatusOnboardingInvalidoException("enviarDocumento", status);
        }
        if (status == StatusOnboarding.INICIADO) {
            this.status = StatusOnboarding.DOCUMENTOS_RECEBIDOS;
        }
        this.revisaoDocumentos++;
    }

    /**
     * Verifica se a solicitacao esta apta a iniciar verificacao no KycProvider. Deve ser chamado
     * pelo use case ANTES de invocar o provider externo para evitar side effect (chamada Celcoin)
     * em solicitacoes ja em estado terminal ou em verificacao.
     *
     * @throws StatusOnboardingInvalidoException se o status atual nao permite disparar verificacao.
     */
    public void validarPodeIniciarVerificacao() {
        if (status != StatusOnboarding.DOCUMENTOS_RECEBIDOS) {
            throw new StatusOnboardingInvalidoException("iniciarVerificacao", status);
        }
    }

    /** Dispara verificacao KYC/KYB; transiciona para {@code EM_VERIFICACAO}. */
    public void marcarEmVerificacao(String idVerificacaoExterna) {
        validarPodeIniciarVerificacao();
        this.idVerificacaoExterna = idVerificacaoExterna;
        this.status = StatusOnboarding.EM_VERIFICACAO;
    }

    /** Finaliza com resultado do webhook KYC/KYB (pre-PLD). */
    public void finalizar(StatusOnboarding statusFinal) {
        if (!statusFinal.isFinal()) {
            throw new StatusOnboardingInvalidoException("finalizar", statusFinal);
        }
        if (status != StatusOnboarding.EM_VERIFICACAO) {
            throw new StatusOnboardingInvalidoException("finalizar", status);
        }
        this.status = statusFinal;
    }

    /**
     * Move {@code APROVADO} (pos-KYC/KYB) para {@code APROVADO_FINAL} (pos-PLD limpo). So aceita a
     * partir de {@code APROVADO}.
     */
    public void marcarAprovadoFinal() {
        if (status != StatusOnboarding.APROVADO) {
            throw new StatusOnboardingInvalidoException("marcarAprovadoFinal", status);
        }
        this.status = StatusOnboarding.APROVADO_FINAL;
    }

    /**
     * Move {@code APROVADO} para {@code REPROVADO_PLD} apos hit em base PLD. So aceita a partir de
     * {@code APROVADO}.
     */
    public void reprovarPorPld() {
        if (status != StatusOnboarding.APROVADO) {
            throw new StatusOnboardingInvalidoException("reprovarPorPld", status);
        }
        this.status = StatusOnboarding.REPROVADO_PLD;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public TipoSolicitante getTipo() {
        return tipo;
    }

    public String getDocumento() {
        return documento;
    }

    /** CPF original — disponivel apenas para {@code PESSOA}; {@code null} para {@code EMPRESA}. */
    public String getCpf() {
        return cpf;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public StatusOnboarding getStatus() {
        return status;
    }

    public String getIdVerificacaoExterna() {
        return idVerificacaoExterna;
    }

    public int getRevisaoDocumentos() {
        return revisaoDocumentos;
    }
}
