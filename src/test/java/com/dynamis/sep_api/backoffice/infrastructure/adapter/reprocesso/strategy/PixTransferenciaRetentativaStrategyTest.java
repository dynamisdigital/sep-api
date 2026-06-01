package com.dynamis.sep_api.backoffice.infrastructure.adapter.reprocesso.strategy;

import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import com.dynamis.sep_api.pix.application.dto.StatusDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.usecase.ConsultarStatusDesembolsoPixUseCase;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PixTransferenciaRetentativaStrategyTest {

    private final ConsultarStatusDesembolsoPixUseCase consultarStatus = mock(ConsultarStatusDesembolsoPixUseCase.class);
    private final PixTransferenciaRetentativaStrategy strategy =
            new PixTransferenciaRetentativaStrategy(consultarStatus);

    @Test
    void tipoSuportado_ehPixTransferencia() {
        assertThat(strategy.tipoSuportado()).isEqualTo(TipoChamadaProvider.PIX_TRANSFERENCIA);
    }

    @Test
    void retentar_reconsultaStatusESinaliza_semReenviar() {
        UUID id = UUID.randomUUID();
        when(consultarStatus.executar(any()))
                .thenReturn(new StatusDesembolsoPixResult(
                        id,
                        UUID.randomUUID(),
                        StatusPixTransferencia.SOLICITADA,
                        new BigDecimal("10000.00"),
                        "us****om",
                        false));

        ResultadoReprocesso res = strategy.retentar(id);

        assertThat(res.status()).isEqualTo(StatusReprocesso.SUCESSO);
        assertThat(res.mensagemTecnica()).contains("SOLICITADA").contains("reenvio nao permitido");
        verify(consultarStatus).executar(any());
    }

    @Test
    void retentar_providerIndisponivel_falhaSemFalsoSucesso() {
        UUID id = UUID.randomUUID();
        when(consultarStatus.executar(any()))
                .thenReturn(new StatusDesembolsoPixResult(
                        id,
                        UUID.randomUUID(),
                        StatusPixTransferencia.SOLICITADA,
                        new BigDecimal("10000.00"),
                        "us****om",
                        true));

        ResultadoReprocesso res = strategy.retentar(id);

        assertThat(res.status()).isEqualTo(StatusReprocesso.FALHA);
        assertThat(res.mensagemTecnica()).contains("indisponivel");
    }

    @Test
    void retentar_transferenciaInexistente_falha() {
        when(consultarStatus.executar(any())).thenThrow(new RecursoNaoEncontradoException("PIX-404", "nao encontrada"));

        ResultadoReprocesso res = strategy.retentar(UUID.randomUUID());

        assertThat(res.status()).isEqualTo(StatusReprocesso.FALHA);
    }
}
