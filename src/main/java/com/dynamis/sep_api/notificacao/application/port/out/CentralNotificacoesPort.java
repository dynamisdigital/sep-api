package com.dynamis.sep_api.notificacao.application.port.out;

import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import org.springframework.data.domain.Page;

import java.util.Optional;
import java.util.UUID;

/**
 * Leitura e marcacao da central de notificacoes (ADR 0021 §3 e §9). Toda operacao recebe o dono e
 * se restringe ao canal {@code IN_APP}: o recorte e da porta, nao de quem chama.
 */
public interface CentralNotificacoesPort {

    /** Pagina das notificacoes do usuario, da mais recente para a mais antiga, desempatando por id. */
    Page<Notificacao> listar(UUID usuarioId, int pagina, int tamanho);

    long contarNaoLidas(UUID usuarioId);

    /** Busca a notificacao do usuario travando a linha ate o fim da transacao de quem chama. */
    Optional<Notificacao> buscarParaAtualizar(UUID usuarioId, UUID notificacaoId);

    /** Grava a leitura, na transacao de quem chama. */
    void registrarLeitura(Notificacao notificacao);
}
