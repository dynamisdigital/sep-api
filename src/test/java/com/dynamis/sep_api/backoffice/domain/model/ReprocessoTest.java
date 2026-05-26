package com.dynamis.sep_api.backoffice.domain.model;

import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReprocessoTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 5, 26, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void paraWebhook_nasceComStatusPendente() {
        UUID webhook = UUID.randomUUID();
        UUID operador = UUID.randomUUID();

        Reprocesso r = Reprocesso.paraWebhook(null, webhook, AGORA, operador);

        assertThat(r.getId()).isNotNull();
        assertThat(r.getTipo()).isEqualTo(Reprocesso.Tipo.WEBHOOK);
        assertThat(r.getTipoChamada()).isNull();
        assertThat(r.getIdentificadorExterno()).isEqualTo(webhook.toString());
        assertThat(r.getStatus()).isEqualTo(StatusReprocesso.PENDENTE);
        assertThat(r.getDataDisparo()).isEqualTo(AGORA);
        assertThat(r.getDisparadoPor()).isEqualTo(operador);
        assertThat(r.getResultado()).isNull();
    }

    @Test
    void paraProvider_exigeTipoChamadaEEntidade() {
        UUID entidade = UUID.randomUUID();
        UUID operador = UUID.randomUUID();

        Reprocesso r = Reprocesso.paraProvider(null, TipoChamadaProvider.KYC, entidade, AGORA, operador);

        assertThat(r.getTipo()).isEqualTo(Reprocesso.Tipo.PROVIDER);
        assertThat(r.getTipoChamada()).isEqualTo(TipoChamadaProvider.KYC);
        assertThat(r.getIdentificadorExterno()).isEqualTo(entidade.toString());
    }

    @Test
    void paraProvider_tipoChamadaNulo_lanca() {
        assertThatThrownBy(() ->
                        Reprocesso.paraProvider(null, null, UUID.randomUUID(), AGORA, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void concluir_pendenteToSucesso() {
        Reprocesso r = Reprocesso.paraWebhook(null, UUID.randomUUID(), AGORA, UUID.randomUUID());

        r.concluirComSucesso("HTTP 200");

        assertThat(r.getStatus()).isEqualTo(StatusReprocesso.SUCESSO);
        assertThat(r.getResultado()).isEqualTo("HTTP 200");
    }

    @Test
    void concluir_pendenteToFalha() {
        Reprocesso r = Reprocesso.paraWebhook(null, UUID.randomUUID(), AGORA, UUID.randomUUID());

        r.concluirComFalha("HTTP 500");

        assertThat(r.getStatus()).isEqualTo(StatusReprocesso.FALHA);
        assertThat(r.getResultado()).isEqualTo("HTTP 500");
    }

    @Test
    void concluir_duasVezes_lanca() {
        Reprocesso r = Reprocesso.paraWebhook(null, UUID.randomUUID(), AGORA, UUID.randomUUID());
        r.concluirComSucesso("ok");

        assertThatIllegalStateException().isThrownBy(() -> r.concluirComFalha("nope"));
        assertThatIllegalStateException().isThrownBy(() -> r.concluirComSucesso("again"));
    }
}
