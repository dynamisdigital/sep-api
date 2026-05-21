package com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura;

import com.dynamis.sep_api.contratos.application.port.out.dto.RequisicaoEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.RespostaEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.StatusEnvelopeProvider;
import com.dynamis.sep_api.contratos.application.port.out.exception.EnvelopeNaoEncontradoException;
import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeAssinaturaDigitalProviderTest {

    private final FakeAssinaturaDigitalProvider provider = new FakeAssinaturaDigitalProvider();

    @Test
    void enviar_retornaIdEnvelopeDeterministicoPorIdempotencyKey() {
        RequisicaoEnvioAssinatura req = req("idemp-abc");

        RespostaEnvioAssinatura r1 = provider.enviarParaAssinatura(new byte[] {1, 2}, req, "corr");
        RespostaEnvioAssinatura r2 = provider.enviarParaAssinatura(new byte[] {1, 2}, req, "corr");

        assertThat(r1.idEnvelopeExterno()).isEqualTo("fake-env-idemp-abc");
        assertThat(r2.idEnvelopeExterno()).isEqualTo(r1.idEnvelopeExterno());
    }

    @Test
    void baixarDocumentoAssinado_retornaPdfStub() {
        byte[] pdf = provider.baixarDocumentoAssinado("fake-env-x");

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void consultarStatus_envelopeDesconhecido_lancaNaoEncontrado() {
        assertThatThrownBy(() -> provider.consultarStatus("fake-env-unknown"))
                .isInstanceOf(EnvelopeNaoEncontradoException.class)
                .hasMessageContaining("fake-env-unknown");
    }

    @Test
    void consultarStatus_aposEnvio_retornaEnviado() {
        RequisicaoEnvioAssinatura req = req("idemp-fluxo");
        RespostaEnvioAssinatura r = provider.enviarParaAssinatura(new byte[] {1}, req, "corr");

        StatusEnvelopeProvider s = provider.consultarStatus(r.idEnvelopeExterno());

        assertThat(s.status()).isEqualTo(StatusEnvelope.ENVIADO);
    }

    @Test
    void setStatus_simulaTransicao() {
        RequisicaoEnvioAssinatura req = req("idemp-set");
        RespostaEnvioAssinatura r = provider.enviarParaAssinatura(new byte[] {1}, req, "corr");

        provider.setStatus(r.idEnvelopeExterno(), StatusEnvelope.RECUSADO);

        assertThat(provider.consultarStatus(r.idEnvelopeExterno()).status()).isEqualTo(StatusEnvelope.RECUSADO);
    }

    private RequisicaoEnvioAssinatura req(String key) {
        return new RequisicaoEnvioAssinatura(UUID.randomUUID(), UUID.randomUUID(), "x@y.com", "Tomador", key);
    }
}
