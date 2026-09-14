package com.dynamis.sep_api.notificacao.application.usecase;

import com.dynamis.sep_api.notificacao.application.exception.PaginacaoInvalidaException;
import com.dynamis.sep_api.notificacao.application.port.out.CentralNotificacoesPort;
import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Leituras da central do usuario autenticado: lista e contador de nao lidas (ADR 0021 §9). O usuario
 * vem sempre de quem chama — o controller o deriva do principal — e nunca de parametro da requisicao.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarCentralNotificacoesUseCase {

    static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final CentralNotificacoesPort central;

    public ConsultarCentralNotificacoesUseCase(CentralNotificacoesPort central) {
        this.central = central;
    }

    public Page<Notificacao> listar(UUID usuarioId, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO_PAGINA) {
            throw new PaginacaoInvalidaException();
        }
        return central.listar(usuarioId, pagina, tamanho);
    }

    public long contarNaoLidas(UUID usuarioId) {
        return central.contarNaoLidas(usuarioId);
    }
}
