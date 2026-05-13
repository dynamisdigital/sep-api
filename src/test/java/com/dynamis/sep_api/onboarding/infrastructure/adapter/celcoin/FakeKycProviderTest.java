package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoVerificacaoKyc;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaInicioVerificacao;
import com.dynamis.sep_api.onboarding.application.port.out.dto.ResultadoKycProvider;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FakeKycProviderTest {

    private final FakeKycProvider provider = new FakeKycProvider();

    @Test
    void iniciarVerificacaoRetornaIdDeterministicoBaseadoNaSolicitacao() {
        UUID solicitacaoId = UUID.randomUUID();
        RequisicaoVerificacaoKyc req = new RequisicaoVerificacaoKyc(
                solicitacaoId,
                UUID.randomUUID(),
                "52998224725",
                "Joao",
                LocalDate.of(1990, 1, 1),
                List.of(new RequisicaoVerificacaoKyc.DocumentoMetadados(TipoDocumento.RG, "h", 100L, "image/jpeg")));

        RespostaInicioVerificacao resposta = provider.iniciarVerificacao(req, "corr-1");

        assertThat(resposta.idVerificacaoExterna()).isEqualTo("fake-" + solicitacaoId);
        assertThat(resposta.statusInicial()).isEqualTo("PROCESSING");
    }

    @Test
    void consultarResultadoSempreFinalizadoAPROVADO() {
        ResultadoKycProvider resultado = provider.consultarResultado("fake-123", "corr-2");

        assertThat(resultado).isInstanceOf(ResultadoKycProvider.Finalizado.class);
        ResultadoKycProvider.Finalizado fin = (ResultadoKycProvider.Finalizado) resultado;
        assertThat(fin.statusFinal()).isEqualTo(StatusOnboarding.APROVADO);
        assertThat(fin.payloadProvider()).contains("\"status\":\"APPROVED\"");
    }
}
