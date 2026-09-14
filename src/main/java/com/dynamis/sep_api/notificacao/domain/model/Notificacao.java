package com.dynamis.sep_api.notificacao.domain.model;

import com.dynamis.sep_api.notificacao.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.Entrega;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.fasterxml.uuid.Generators;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Notificacao enderecada a um unico usuario (Sprint 38, ADR 0021).
 *
 * <p>Tres coisas independentes: o que aconteceu ({@link OrigemNotificacao}), o que se diz ({@link
 * ConteudoNotificacao}) e como chegou ({@link Entrega}). A leitura e uma quarta, e so existe no
 * canal {@code IN_APP} — historico de e-mail nao vira item nao lido.
 *
 * <p>Sem JPA: a persistencia fica em {@code infrastructure.persistence}, com mapeamento explicito
 * (ADR 0007). Instantes chegam por parametro, vindos do {@code Clock} injetado de quem chama.
 */
public final class Notificacao {

    private final UUID id;
    private final UUID usuarioId;
    private final OrigemNotificacao origem;
    private final ConteudoNotificacao conteudo;
    private final OffsetDateTime criadaEm;
    private Entrega entrega;
    private OffsetDateTime lidaEm;

    private Notificacao(
            UUID id,
            UUID usuarioId,
            OrigemNotificacao origem,
            ConteudoNotificacao conteudo,
            Entrega entrega,
            OffsetDateTime criadaEm,
            OffsetDateTime lidaEm) {
        this.id = Objects.requireNonNull(id, "id obrigatorio");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId obrigatorio");
        this.origem = Objects.requireNonNull(origem, "origem obrigatoria");
        this.conteudo = Objects.requireNonNull(conteudo, "conteudo obrigatorio");
        this.entrega = Objects.requireNonNull(entrega, "entrega obrigatoria");
        this.criadaEm = Objects.requireNonNull(criadaEm, "criadaEm obrigatorio");
        if (lidaEm != null && entrega.canal() != CanalNotificacao.IN_APP) {
            throw new IllegalArgumentException("so notificacao IN_APP tem leitura");
        }
        this.lidaEm = lidaEm;
    }

    /** Notificacao da central: nasce {@code DISPONIVEL} e nao lida. */
    public static Notificacao disponibilizarInApp(
            UUID usuarioId, OrigemNotificacao origem, ConteudoNotificacao conteudo, OffsetDateTime agora) {
        return new Notificacao(novoId(), usuarioId, origem, conteudo, Entrega.inAppDisponivel(agora), agora, null);
    }

    /** Tentativa de e-mail: nasce {@code PENDENTE}, antes da chamada ao provider. */
    public static Notificacao registrarEmail(
            UUID usuarioId, OrigemNotificacao origem, ConteudoNotificacao conteudo, OffsetDateTime agora) {
        return new Notificacao(novoId(), usuarioId, origem, conteudo, Entrega.emailPendente(agora), agora, null);
    }

    /** Recompoe uma notificacao persistida, reaplicando as invariantes. */
    public static Notificacao reconstituir(
            UUID id,
            UUID usuarioId,
            OrigemNotificacao origem,
            ConteudoNotificacao conteudo,
            Entrega entrega,
            OffsetDateTime criadaEm,
            OffsetDateTime lidaEm) {
        return new Notificacao(id, usuarioId, origem, conteudo, entrega, criadaEm, lidaEm);
    }

    private static UUID novoId() {
        return Generators.timeBasedReorderedGenerator().generate();
    }

    /**
     * Marca como lida. Idempotente: a segunda marcacao retorna {@code false} e preserva o instante da
     * primeira (ADR 0021 §7).
     */
    public boolean marcarLida(OffsetDateTime agora) {
        Objects.requireNonNull(agora, "agora obrigatorio");
        if (entrega.canal() != CanalNotificacao.IN_APP) {
            throw new IllegalStateException("so notificacao IN_APP tem leitura");
        }
        if (lidaEm != null) {
            return false;
        }
        lidaEm = agora;
        return true;
    }

    public void confirmarEnvio(OffsetDateTime agora) {
        entrega = entrega.aposEnvio(agora);
    }

    public void registrarSimulacao(OffsetDateTime agora) {
        entrega = entrega.aposSimulacao(agora);
    }

    public void registrarFalha(String motivo, OffsetDateTime agora) {
        entrega = entrega.aposFalha(motivo, agora);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public OrigemNotificacao getOrigem() {
        return origem;
    }

    public ConteudoNotificacao getConteudo() {
        return conteudo;
    }

    public Entrega getEntrega() {
        return entrega;
    }

    public OffsetDateTime getCriadaEm() {
        return criadaEm;
    }

    public Optional<OffsetDateTime> getLidaEm() {
        return Optional.ofNullable(lidaEm);
    }
}
