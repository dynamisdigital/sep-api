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
    void mapearStatusFinalConverteAPPROVEDParaAPROVADO() {
        assertThat(CelcoinKycMapper.mapearStatusFinal("APPROVED")).isEqualTo(StatusOnboarding.APROVADO);
        assertThat(CelcoinKycMapper.mapearStatusFinal("approved")).isEqualTo(StatusOnboarding.APROVADO);
    }

    @Test
    void mapearStatusFinalConverteREJECTEDParaREPROVADO() {
        assertThat(CelcoinKycMapper.mapearStatusFinal("REJECTED")).isEqualTo(StatusOnboarding.REPROVADO);
    }

    @Test
    void mapearStatusFinalConvertePENDINGEPROCESSINGParaPENDENCIA() {
        assertThat(CelcoinKycMapper.mapearStatusFinal("PENDING")).isEqualTo(StatusOnboarding.PENDENCIA);
        assertThat(CelcoinKycMapper.mapearStatusFinal("PROCESSING")).isEqualTo(StatusOnboarding.PENDENCIA);
    }

    @Test
    void mapearStatusFinalConverteStatusDesconhecidoParaPENDENCIA() {
        assertThat(CelcoinKycMapper.mapearStatusFinal("FOO")).isEqualTo(StatusOnboarding.PENDENCIA);
        assertThat(CelcoinKycMapper.mapearStatusFinal(null)).isEqualTo(StatusOnboarding.PENDENCIA);
    }

    @Test
    void toResultadoKycMantemPayloadCru() {
        ResultadoKycProvider r =
                mapper.toResultadoKyc(new CelcoinKycResultadoResponse("ext-1", "APPROVED", null), "{\"raw\":true}");

        assertThat(r.statusFinal()).isEqualTo(StatusOnboarding.APROVADO);
        assertThat(r.payloadProvider()).isEqualTo("{\"raw\":true}");
    }
}
