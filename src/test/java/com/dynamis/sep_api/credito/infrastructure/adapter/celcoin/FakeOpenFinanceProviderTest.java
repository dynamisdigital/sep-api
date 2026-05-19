package com.dynamis.sep_api.credito.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.credito.application.port.out.dto.MovimentacaoConsolidada;
import com.dynamis.sep_api.credito.application.port.out.dto.RequisicaoConsentimento;
import com.dynamis.sep_api.credito.application.port.out.dto.RespostaConsentimento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FakeOpenFinanceProviderTest {

    private final FakeOpenFinanceProvider provider = new FakeOpenFinanceProvider();

    @Test
    void iniciarConsentimentoRetornaIdExternoComPrefixoFake() {
        UUID prop = UUID.randomUUID();
        RequisicaoConsentimento req =
                new RequisicaoConsentimento(prop, UUID.randomUUID(), "52998224725", "https://sep/cb");

        RespostaConsentimento resp = provider.iniciarConsentimento(req, "corr-1");

        assertThat(resp.idExterno()).startsWith("fake-of-");
        assertThat(resp.urlAutorizacao()).contains(resp.idExterno());
        assertThat(resp.dataExpiracao()).isNotNull();
    }

    @Test
    void iniciarConsentimentoGeraIdsUnicosParaMesmaProposta() {
        // Sprint 9 fix code review Task 9.2: propostaId pode ter historico de consentimentos
        // NEGADO/EXPIRADO + novo PENDENTE; idExterno precisa ser unico por chamada pra nao
        // colidir em findByIdExternoCelcoin.
        UUID prop = UUID.randomUUID();
        RequisicaoConsentimento req =
                new RequisicaoConsentimento(prop, UUID.randomUUID(), "52998224725", "https://sep/cb");

        RespostaConsentimento r1 = provider.iniciarConsentimento(req, "corr-a");
        RespostaConsentimento r2 = provider.iniciarConsentimento(req, "corr-b");

        assertThat(r1.idExterno()).isNotEqualTo(r2.idExterno());
    }

    @Test
    void consultarMovimentacaoRetornaSnapshotConsolidadoAlto() {
        MovimentacaoConsolidada mov = provider.consultarMovimentacao("fake-of-x", "corr-2");

        assertThat(mov.mediaEntradasMensal()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(mov.mediaSaidasMensal()).isEqualByComparingTo(new BigDecimal("7000.00"));
        assertThat(mov.saldoMedio()).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(mov.numeroMesesAvaliados()).isEqualTo(6);
        assertThat(mov.payloadConsolidado()).contains("fake-of-x").contains("\"fonte\":\"fake\"");
    }

    @Test
    void payloadConsolidadoNaoCarregaDadoBancarioBruto() {
        MovimentacaoConsolidada mov = provider.consultarMovimentacao("fake-of-x", "corr-3");
        assertThat(mov.payloadConsolidado())
                .doesNotContain("conta")
                .doesNotContain("agencia")
                .doesNotContain("CPF");
    }
}
