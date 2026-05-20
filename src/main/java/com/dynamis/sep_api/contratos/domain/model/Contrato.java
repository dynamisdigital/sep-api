package com.dynamis.sep_api.contratos.domain.model;

import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Agregado raiz da formalizacao contratual (Sprint 10). Representa o contrato de uma {@code
 * PropostaCredito} aprovada (1:1 com proposta). Mantem o historico imutavel de {@link
 * VersaoContrato} geradas e o estado da maquina de {@link StatusFormalizacao}.
 *
 * <p>Estados permitidos:
 *
 * <pre>
 *   GERADO            -> AGUARDANDO_ACEITE | CANCELADO
 *   AGUARDANDO_ACEITE -> AGUARDANDO_ACEITE (regeneracao) | ACEITO | CANCELADO
 *   ACEITO            -> EM_ASSINATURA (Sprint 11)
 *   EM_ASSINATURA     -> ASSINADO (Sprint 11)
 *   ASSINADO          = final
 *   CANCELADO         = final
 * </pre>
 *
 * <p>UUID v6 gerado via {@code timeBasedReorderedGenerator()}. Construtor publico {@code protected}
 * pra Hibernate; entidades novas devem usar {@link #criar(UUID, UUID, TipoContrato)}.
 */
@Entity
@Table(name = "contrato")
public class Contrato extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "proposta_id", columnDefinition = "uuid", nullable = false, unique = true, updatable = false)
    private UUID propostaId;

    @Column(name = "tomador_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID tomadorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, updatable = false)
    private TipoContrato tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusFormalizacao status;

    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("numero ASC")
    private List<VersaoContrato> versoes = new ArrayList<>();

    protected Contrato() {}

    private Contrato(UUID id, UUID propostaId, UUID tomadorId, TipoContrato tipo) {
        this.id = id;
        this.propostaId = propostaId;
        this.tomadorId = tomadorId;
        this.tipo = tipo;
        this.status = StatusFormalizacao.GERADO;
    }

    public static Contrato criar(UUID propostaId, UUID tomadorId, TipoContrato tipo) {
        Objects.requireNonNull(propostaId, "propostaId obrigatoria");
        Objects.requireNonNull(tomadorId, "tomadorId obrigatorio");
        Objects.requireNonNull(tipo, "tipo obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new Contrato(id, propostaId, tomadorId, tipo);
    }

    /**
     * Adiciona nova versao ao contrato. Primeira chamada transiciona {@code GERADO} ->
     * {@code AGUARDANDO_ACEITE}; chamadas subsequentes em {@code AGUARDANDO_ACEITE} mantem o
     * estado (regeneracao). Lanca {@link ContratoEstadoInvalidoException} se o estado nao
     * permitir.
     */
    public VersaoContrato adicionarVersao(String conteudoTexto, String hashSha256) {
        return adicionarVersao(conteudoTexto, hashSha256, null);
    }

    public VersaoContrato adicionarVersao(String conteudoTexto, String hashSha256, UUID parecerOrigemId) {
        if (!status.permiteNovaVersao()) {
            throw new ContratoEstadoInvalidoException("adicionarVersao", status);
        }
        int proximoNumero =
                versoes.stream().mapToInt(VersaoContrato::getNumero).max().orElse(0) + 1;
        VersaoContrato versao = VersaoContrato.criar(this, proximoNumero, conteudoTexto, hashSha256, parecerOrigemId);
        versoes.add(versao);
        if (status == StatusFormalizacao.GERADO) {
            this.status = StatusFormalizacao.AGUARDANDO_ACEITE;
        }
        return versao;
    }

    /**
     * Marca o contrato como {@code ACEITO}. Aceite e sempre sobre a versao mais recente; este
     * metodo nao persiste o {@link AceiteContrato} (responsabilidade do use case). Lanca {@link
     * ContratoEstadoInvalidoException} se estado nao for {@code AGUARDANDO_ACEITE}.
     */
    public void marcarAceito() {
        if (!status.permiteAceite()) {
            throw new ContratoEstadoInvalidoException("marcarAceito", status);
        }
        this.status = StatusFormalizacao.ACEITO;
    }

    /**
     * Cancela o contrato. Permitido apenas em {@code GERADO} ou {@code AGUARDANDO_ACEITE}. Estado
     * final.
     */
    public void cancelar() {
        if (!status.permiteCancelamento()) {
            throw new ContratoEstadoInvalidoException("cancelar", status);
        }
        this.status = StatusFormalizacao.CANCELADO;
    }

    public Optional<VersaoContrato> versaoVigente() {
        return versoes.stream().max(Comparator.comparingInt(VersaoContrato::getNumero));
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

    public TipoContrato getTipo() {
        return tipo;
    }

    public StatusFormalizacao getStatus() {
        return status;
    }

    public List<VersaoContrato> getVersoes() {
        return Collections.unmodifiableList(versoes);
    }
}
