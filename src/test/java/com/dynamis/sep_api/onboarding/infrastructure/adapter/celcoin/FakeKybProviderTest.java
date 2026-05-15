package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoKyb;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaKyb;
import com.dynamis.sep_api.onboarding.domain.vo.SituacaoCadastral;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FakeKybProviderTest {

    private final FakeKybProvider provider = new FakeKybProvider();

    @Test
    void consultarCnpjRetornaSituacaoAtivaComRepresentante() {
        UUID solicitacaoId = UUID.randomUUID();
        RequisicaoKyb req = new RequisicaoKyb(
                solicitacaoId,
                UUID.randomUUID(),
                "11222333000181",
                "ACME LTDA",
                List.of(new RequisicaoKyb.DocumentoMetadadosKyb("CONTRATO_SOCIAL", "h", 100L, "application/pdf")));

        RespostaKyb resposta = provider.consultarCnpj(req, "corr-1");

        assertThat(resposta.situacaoCadastral()).isEqualTo(SituacaoCadastral.ATIVA);
        assertThat(resposta.situacaoCadastral().habilitaProgressao()).isTrue();
        assertThat(resposta.razaoSocial()).isEqualTo("ACME LTDA");
        assertThat(resposta.representantes()).hasSize(1);
        assertThat(resposta.representantes().get(0).cpf()).isEqualTo("52998224725");
        assertThat(resposta.payloadProvider()).contains("\"registration_status\":\"ACTIVE\"");
    }
}
