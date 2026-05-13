package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoVerificacaoKyc;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaInicioVerificacao;
import com.dynamis.sep_api.onboarding.application.port.out.dto.ResultadoKycProvider;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycRequest;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycResponse;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycResultadoResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CelcoinKycMapperTest {

    private final CelcoinKycMapper mapper = new CelcoinKycMapper() {};

    @Test
    void toCelcoinRequestSerializaDocumentosComoStringDoEnum() {
        UUID solicitacaoId = UUID.randomUUID();
        RequisicaoVerificacaoKyc req = new RequisicaoVerificacaoKyc(
                solicitacaoId,
                UUID.randomUUID(),
                "52998224725",
                "Joao",
                LocalDate.of(1990, 1, 1),
                List.of(
                        new RequisicaoVerificacaoKyc.DocumentoMetadados(TipoDocumento.RG, "h1", 1L, "image/jpeg"),
                        new RequisicaoVerificacaoKyc.DocumentoMetadados(TipoDocumento.SELFIE, "h2", 2L, "image/png")));

        CelcoinKycRequest celcoin = mapper.toCelcoinRequest(req);

        assertThat(celcoin.externalId()).isEqualTo(solicitacaoId.toString());
        assertThat(celcoin.cpf()).isEqualTo("52998224725");
        assertThat(celcoin.documentos())
                .extracting(CelcoinKycRequest.DocumentoRef::tipo)
                .containsExactly("RG", "SELFIE");
    }

    @Test
    void toRespostaInicioCopiaIdVerificacaoEStatus() {
        RespostaInicioVerificacao resp = mapper.toRespostaInicio(new CelcoinKycResponse("ext-123", "PROCESSING"));

        assertThat(resp.idVerificacaoExterna()).isEqualTo("ext-123");
        assertThat(resp.statusInicial()).isEqualTo("PROCESSING");
    }

    @Test
    void approvedViraFinalizadoAPROVADO() {
        ResultadoKycProvider r =
                mapper.toResultadoKyc(new CelcoinKycResultadoResponse("ext", "APPROVED", null), "{\"raw\":1}");

        assertThat(r).isInstanceOf(ResultadoKycProvider.Finalizado.class);
        assertThat(((ResultadoKycProvider.Finalizado) r).statusFinal()).isEqualTo(StatusOnboarding.APROVADO);
        assertThat(r.payloadProvider()).isEqualTo("{\"raw\":1}");
    }

    @Test
    void rejectedViraFinalizadoREPROVADO() {
        ResultadoKycProvider r = mapper.toResultadoKyc(
                new CelcoinKycResultadoResponse("ext", "REJECTED", "documentos inconsistentes"), "{}");

        assertThat(((ResultadoKycProvider.Finalizado) r).statusFinal()).isEqualTo(StatusOnboarding.REPROVADO);
        assertThat(((ResultadoKycProvider.Finalizado) r).motivo()).isEqualTo("documentos inconsistentes");
    }

    @Test
    void pendingViraFinalizadoPENDENCIA() {
        ResultadoKycProvider r = mapper.toResultadoKyc(new CelcoinKycResultadoResponse("ext", "PENDING", null), "{}");

        assertThat(((ResultadoKycProvider.Finalizado) r).statusFinal()).isEqualTo(StatusOnboarding.PENDENCIA);
    }

    @Test
    void processingViraEmAndamentoNaoFinaliza() {
        ResultadoKycProvider r =
                mapper.toResultadoKyc(new CelcoinKycResultadoResponse("ext", "PROCESSING", null), "{}");

        assertThat(r).isInstanceOf(ResultadoKycProvider.EmAndamento.class);
    }

    @Test
    void desconhecidoViraEmAndamentoSeguroNaoFinaliza() {
        ResultadoKycProvider r1 = mapper.toResultadoKyc(new CelcoinKycResultadoResponse("ext", "FOO", null), "{}");
        ResultadoKycProvider r2 = mapper.toResultadoKyc(new CelcoinKycResultadoResponse("ext", null, null), "{}");
        ResultadoKycProvider r3 = mapper.toResultadoKyc(null, "{}");

        assertThat(r1).isInstanceOf(ResultadoKycProvider.EmAndamento.class);
        assertThat(r2).isInstanceOf(ResultadoKycProvider.EmAndamento.class);
        assertThat(r3).isInstanceOf(ResultadoKycProvider.EmAndamento.class);
    }
}
