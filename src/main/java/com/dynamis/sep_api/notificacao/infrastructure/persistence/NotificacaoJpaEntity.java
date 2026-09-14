package com.dynamis.sep_api.notificacao.infrastructure.persistence;

import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.Entrega;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.Referencia;
import com.dynamis.sep_api.notificacao.domain.vo.SituacaoEntrega;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.TipoReferencia;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Linha da tabela {@code notificacao} (V61). Existe so para a persistencia: o agregado
 * {@link Notificacao} nao conhece JPA (ADR 0007, ADR 0021 §10), e a traducao entre os dois e feita
 * aqui, campo a campo, para que toda linha lida passe de novo pelas invariantes do dominio.
 */
@Entity
@Table(name = "notificacao")
public class NotificacaoJpaEntity extends EntidadeAuditavel {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, updatable = false)
    private TipoNotificacao tipo;

    @Column(name = "origem_id", nullable = false, length = 100, updatable = false)
    private String origemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 20, updatable = false)
    private CanalNotificacao canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    private SituacaoEntrega situacao;

    @Column(name = "situacao_atualizada_em", nullable = false)
    private OffsetDateTime situacaoAtualizadaEm;

    @Column(name = "motivo_falha", length = 200)
    private String motivoFalha;

    @Column(name = "titulo", nullable = false, length = 120, updatable = false)
    private String titulo;

    @Column(name = "mensagem", nullable = false, length = 500, updatable = false)
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(name = "referencia_tipo", length = 20, updatable = false)
    private TipoReferencia referenciaTipo;

    @Column(name = "referencia_id", updatable = false)
    private UUID referenciaId;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private OffsetDateTime criadaEm;

    @Column(name = "lida_em")
    private OffsetDateTime lidaEm;

    protected NotificacaoJpaEntity() {
        // JPA
    }

    public static NotificacaoJpaEntity de(Notificacao notificacao) {
        NotificacaoJpaEntity entidade = new NotificacaoJpaEntity();
        entidade.id = notificacao.getId();
        entidade.usuarioId = notificacao.getUsuarioId();
        entidade.tipo = notificacao.getOrigem().tipo();
        entidade.origemId = notificacao.getOrigem().id();
        entidade.canal = notificacao.getEntrega().canal();
        entidade.titulo = notificacao.getConteudo().titulo();
        entidade.mensagem = notificacao.getConteudo().mensagem();
        Referencia referencia = notificacao.getConteudo().referencia();
        if (referencia != null) {
            entidade.referenciaTipo = referencia.tipo();
            entidade.referenciaId = referencia.id();
        }
        entidade.criadaEm = notificacao.getCriadaEm();
        entidade.aplicarEstado(notificacao);
        return entidade;
    }

    /** Copia so o que muda depois da criacao: entrega e leitura. */
    public void aplicarEstado(Notificacao notificacao) {
        Entrega entrega = notificacao.getEntrega();
        situacao = entrega.situacao();
        situacaoAtualizadaEm = entrega.atualizadaEm();
        motivoFalha = entrega.motivoFalha();
        lidaEm = notificacao.getLidaEm().orElse(null);
    }

    /** Copia so a leitura: a central nao toca a entrega. */
    public void aplicarLeitura(Notificacao notificacao) {
        lidaEm = notificacao.getLidaEm().orElse(null);
    }

    public Notificacao paraDominio() {
        Referencia referencia = referenciaTipo == null ? null : new Referencia(referenciaTipo, referenciaId);
        return Notificacao.reconstituir(
                id,
                usuarioId,
                new OrigemNotificacao(tipo, origemId),
                new ConteudoNotificacao(titulo, mensagem, referencia),
                new Entrega(canal, situacao, situacaoAtualizadaEm, motivoFalha),
                criadaEm,
                lidaEm);
    }
}
