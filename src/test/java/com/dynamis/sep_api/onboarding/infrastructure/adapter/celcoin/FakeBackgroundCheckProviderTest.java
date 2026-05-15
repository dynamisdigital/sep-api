package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaPld;
import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FakeBackgroundCheckProviderTest {

    private final FakeBackgroundCheckProvider provider = new FakeBackgroundCheckProvider();

    @AfterEach
    void limpar() {
        FakeBackgroundCheckProvider.limparHits();
    }

    @Test
    void consultarPessoaDefaultRetornaLimpoCobrindoAs4BasesObrigatorias() {
        RequisicaoPld req =
                RequisicaoPld.comBasesObrigatorias(UUID.randomUUID(), AlvoPld.PESSOA, "Joao", "52998224725");

        RespostaPld resp = provider.consultarPessoa(req, "corr-1");

        assertThat(resp.limpo()).isTrue();
        assertThat(resp.hits()).isEmpty();
        assertThat(resp.basesConsultadas())
                .containsExactlyInAnyOrder(BasePld.COAF, BasePld.OFAC, BasePld.INTERPOL, BasePld.MTE);
    }

    @Test
    void consultarEmpresaDefaultRetornaLimpoCobrindoAs4BasesObrigatorias() {
        RequisicaoPld req =
                RequisicaoPld.comBasesObrigatorias(UUID.randomUUID(), AlvoPld.EMPRESA, "ACME LTDA", "11222333000181");

        RespostaPld resp = provider.consultarEmpresa(req, "corr-2");

        assertThat(resp.limpo()).isTrue();
        assertThat(resp.basesConsultadas())
                .containsExactlyInAnyOrder(BasePld.COAF, BasePld.OFAC, BasePld.INTERPOL, BasePld.MTE);
    }

    @Test
    void documentoMarcadoComoHitRetornaHitEmTodasBasesObrigatorias() {
        FakeBackgroundCheckProvider.marcarDocumentoComoHit("11222333000181");
        RequisicaoPld req =
                RequisicaoPld.comBasesObrigatorias(UUID.randomUUID(), AlvoPld.EMPRESA, "ACME LTDA", "11222333000181");

        RespostaPld resp = provider.consultarEmpresa(req, "corr-3");

        assertThat(resp.limpo()).isFalse();
        assertThat(resp.hits()).hasSize(4);
        assertThat(resp.hits())
                .extracting(h -> h.base())
                .containsExactlyInAnyOrder(BasePld.COAF, BasePld.OFAC, BasePld.INTERPOL, BasePld.MTE);
        assertThat(resp.basesConsultadas())
                .containsExactlyInAnyOrder(BasePld.COAF, BasePld.OFAC, BasePld.INTERPOL, BasePld.MTE);
        assertThat(resp.hits().get(0).severidade()).isEqualTo(SeveridadePld.ALTA);
    }
}
