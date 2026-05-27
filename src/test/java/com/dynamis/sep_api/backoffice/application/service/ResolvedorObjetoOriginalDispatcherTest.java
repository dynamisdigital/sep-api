package com.dynamis.sep_api.backoffice.application.service;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.application.port.out.ObjetoOriginalQueryPort;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedorObjetoOriginalDispatcherTest {

    @Test
    void semStrategies_devolveEmpty() {
        ResolvedorObjetoOriginalDispatcher dispatcher = new ResolvedorObjetoOriginalDispatcher(List.of());
        assertThat(dispatcher.resolver(TipoEntidadeReferenciada.ONBOARDING, UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void strategyRegistrada_dispatcha() {
        UUID id = UUID.randomUUID();
        ObjetoOriginalResumo esperado =
                new ObjetoOriginalResumo(TipoEntidadeReferenciada.PROPOSTA, id, "EM_ANALISE", "Proposta");
        ObjetoOriginalQueryPort strategy = strategyFixa(TipoEntidadeReferenciada.PROPOSTA, esperado);

        ResolvedorObjetoOriginalDispatcher dispatcher = new ResolvedorObjetoOriginalDispatcher(List.of(strategy));

        assertThat(dispatcher.resolver(TipoEntidadeReferenciada.PROPOSTA, id)).contains(esperado);
    }

    @Test
    void strategyLancaExcecao_devolveEmpty() {
        ObjetoOriginalQueryPort strategy = new ObjetoOriginalQueryPort() {
            @Override
            public TipoEntidadeReferenciada tipoSuportado() {
                return TipoEntidadeReferenciada.CONTRATO;
            }

            @Override
            public Optional<ObjetoOriginalResumo> buscar(UUID entidadeId) {
                throw new RuntimeException("boom");
            }
        };
        ResolvedorObjetoOriginalDispatcher dispatcher = new ResolvedorObjetoOriginalDispatcher(List.of(strategy));

        assertThat(dispatcher.resolver(TipoEntidadeReferenciada.CONTRATO, UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void tipoNaoMapeado_devolveEmpty() {
        ObjetoOriginalQueryPort strategy = strategyFixa(
                TipoEntidadeReferenciada.PROPOSTA,
                new ObjetoOriginalResumo(TipoEntidadeReferenciada.PROPOSTA, UUID.randomUUID(), "X", "Y"));
        ResolvedorObjetoOriginalDispatcher dispatcher = new ResolvedorObjetoOriginalDispatcher(List.of(strategy));

        assertThat(dispatcher.resolver(TipoEntidadeReferenciada.WEBHOOK_EVENT_LOG, UUID.randomUUID()))
                .isEmpty();
    }

    private static ObjetoOriginalQueryPort strategyFixa(TipoEntidadeReferenciada tipo, ObjetoOriginalResumo resumo) {
        return new ObjetoOriginalQueryPort() {
            @Override
            public TipoEntidadeReferenciada tipoSuportado() {
                return tipo;
            }

            @Override
            public Optional<ObjetoOriginalResumo> buscar(UUID entidadeId) {
                return Optional.of(resumo);
            }
        };
    }
}
