package com.dynamis.sep_api.notificacao.application.usecase;

import com.dynamis.sep_api.notificacao.application.exception.NotificacaoNaoEncontradaException;
import com.dynamis.sep_api.notificacao.application.port.out.CentralNotificacoesPort;
import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Marca uma notificacao da central como lida (ADR 0021 §7 e §9).
 *
 * <p>A linha e travada na leitura: duas marcacoes concorrentes serializam, e a segunda encontra a
 * leitura ja gravada — o instante preservado e o da primeira, como o agregado promete. Notificacao
 * de outro usuario ou fora do recorte {@code IN_APP} nao e encontrada pela porta e vira o mesmo 404
 * de uma inexistente.
 */
@Service
public class MarcarNotificacaoLidaUseCase {

    private final CentralNotificacoesPort central;
    private final Clock clock;

    public MarcarNotificacaoLidaUseCase(CentralNotificacoesPort central, Clock clock) {
        this.central = central;
        this.clock = clock;
    }

    @Transactional
    public Notificacao marcar(UUID usuarioId, UUID notificacaoId) {
        Notificacao notificacao = central.buscarParaAtualizar(usuarioId, notificacaoId)
                .orElseThrow(NotificacaoNaoEncontradaException::new);
        if (notificacao.marcarLida(agora())) {
            central.registrarLeitura(notificacao);
        }
        return notificacao;
    }

    /**
     * Na precisao que o PostgreSQL guarda. Sem truncar, a primeira marcacao devolveria nanossegundos
     * e a segunda o valor relido do banco — o mesmo {@code lidaEm} com dois valores para o cliente.
     */
    private OffsetDateTime agora() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
