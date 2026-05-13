package com.dynamis.sep_api.onboarding.domain.model;

import com.dynamis.sep_api.onboarding.domain.exception.StatusOnboardingInvalidoException;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Agregado raiz do modulo {@code onboarding}. Representa uma solicitacao de verificacao KYC de
 * pessoa fisica.
 *
 * <p>UUID v6 gerado via {@code timeBasedReorderedGenerator()}. Construtor publico {@code
 * protected} para satisfazer Hibernate; entidades novas devem usar {@link #criar(UUID, Cpf,
 * String, LocalDate)}.
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

    @Column(name = "cpf", nullable = false, length = 11, updatable = false)
    private String cpf;

    @Column(name = "nome_completo", nullable = false, length = 255)
    private String nomeCompleto;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusOnboarding status;

    @Column(name = "id_verificacao_externa", length = 120)
    private String idVerificacaoExterna;

    @Column(name = "revisao_documentos", nullable = false)
    private int revisaoDocumentos;

    protected SolicitacaoOnboarding() {
        // requerido pelo Hibernate
    }

    private SolicitacaoOnboarding(UUID id, UUID usuarioId, Cpf cpf, String nomeCompleto, LocalDate dataNascimento) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.cpf = cpf.valor();
        this.nomeCompleto = nomeCompleto;
        this.dataNascimento = dataNascimento;
        this.status = StatusOnboarding.INICIADO;
        this.revisaoDocumentos = 0;
    }

    public static SolicitacaoOnboarding criar(UUID usuarioId, Cpf cpf, String nomeCompleto, LocalDate dataNascimento) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new SolicitacaoOnboarding(id, usuarioId, cpf, nomeCompleto, dataNascimento);
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

    /** Dispara verificacao KYC; transiciona para {@code EM_VERIFICACAO}. */
    public void marcarEmVerificacao(String idVerificacaoExterna) {
        validarPodeIniciarVerificacao();
        this.idVerificacaoExterna = idVerificacaoExterna;
        this.status = StatusOnboarding.EM_VERIFICACAO;
    }

    /** Finaliza com resultado do webhook KYC. */
    public void finalizar(StatusOnboarding statusFinal) {
        if (!statusFinal.isFinal()) {
            throw new StatusOnboardingInvalidoException("finalizar", statusFinal);
        }
        if (status != StatusOnboarding.EM_VERIFICACAO) {
            throw new StatusOnboardingInvalidoException("finalizar", status);
        }
        this.status = statusFinal;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

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
