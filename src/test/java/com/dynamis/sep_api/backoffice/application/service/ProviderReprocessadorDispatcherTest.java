package com.dynamis.sep_api.backoffice.application.service;

import com.dynamis.sep_api.backoffice.application.port.out.ProviderRetentativaStrategy;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ProviderReprocessadorDispatcherTest {

    @Test
    void tipoSemStrategy_lancaTipoReprocessoNaoSuportado() {
        ProviderReprocessadorDispatcher d = new ProviderReprocessadorDispatcher(List.of());

        assertThatExceptionOfType(com.dynamis.sep_api.backoffice.domain.exception.TipoReprocessoNaoSuportadoException.class)
                .isThrownBy(() -> d.reprocessar(TipoChamadaProvider.KYC, UUID.randomUUID()));
    }

    @Test
    void tipoComStrategy_dispatcha() {
        ProviderRetentativaStrategy stub = new ProviderRetentativaStrategy() {
            @Override
            public TipoChamadaProvider tipoSuportado() {
                return TipoChamadaProvider.PLD;
            }

            @Override
            public ResultadoReprocesso retentar(UUID entidadeId) {
                return ResultadoReprocesso.sucesso("ok " + entidadeId);
            }
        };
        ProviderReprocessadorDispatcher d = new ProviderReprocessadorDispatcher(List.of(stub));
        UUID id = UUID.randomUUID();

        ResultadoReprocesso r = d.reprocessar(TipoChamadaProvider.PLD, id);

        assertThat(r.mensagemTecnica()).contains(id.toString());
    }
}
