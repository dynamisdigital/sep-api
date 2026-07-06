package com.dynamis.sep_api.credores.infrastructure.adapter.pix;

import com.dynamis.sep_api.credores.application.dto.PixOperacaoStatusView;
import com.dynamis.sep_api.credores.application.port.out.PixOperacaoStatusQueryPort;
import com.dynamis.sep_api.pix.application.usecase.ConsultarStatusPixPorContratoUseCase;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que implementa {@link PixOperacaoStatusQueryPort} delegando a leitura publica de status Pix
 * ao modulo {@code pix} (Sprint 26 — Gate P3). Converte a {@code StatusPixPublicoView} de {@code pix}
 * na {@link PixOperacaoStatusView} de {@code credores}, mapeando o enum publico para {@code String}
 * na fronteira — o mapa de status permanece em fonte unica dentro de {@code pix}.
 */
@Component
public class PixOperacaoStatusQueryAdapter implements PixOperacaoStatusQueryPort {

    private final ConsultarStatusPixPorContratoUseCase consultarStatusPixPorContrato;

    public PixOperacaoStatusQueryAdapter(ConsultarStatusPixPorContratoUseCase consultarStatusPixPorContrato) {
        this.consultarStatusPixPorContrato = consultarStatusPixPorContrato;
    }

    @Override
    public Optional<PixOperacaoStatusView> consultarPorContrato(UUID contratoId) {
        return consultarStatusPixPorContrato
                .executar(contratoId)
                .map(view -> new PixOperacaoStatusView(view.status().name(), view.valor(), view.atualizadoEm()));
    }
}
