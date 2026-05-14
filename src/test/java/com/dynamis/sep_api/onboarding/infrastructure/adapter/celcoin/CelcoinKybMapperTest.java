package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoKyb;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaKyb;
import com.dynamis.sep_api.onboarding.domain.vo.SituacaoCadastral;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKybRequest;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKybResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CelcoinKybMapperTest {

    private final CelcoinKybMapper mapper = Mappers.getMapper(CelcoinKybMapper.class);

    @Test
    void toCelcoinRequestMapeiaCamposObrigatorios() {
        UUID id = UUID.randomUUID();
        RequisicaoKyb req = new RequisicaoKyb(
                id,
                UUID.randomUUID(),
                "11222333000181",
                "ACME LTDA",
                List.of(new RequisicaoKyb.DocumentoMetadadosKyb("CCMEI", "sha-1", 10L, "application/pdf")));

        CelcoinKybRequest celcoin = mapper.toCelcoinRequest(req);

        assertThat(celcoin.externalId()).isEqualTo(id.toString());
        assertThat(celcoin.cnpj()).isEqualTo("11222333000181");
        assertThat(celcoin.razaoSocial()).isEqualTo("ACME LTDA");
        assertThat(celcoin.documentos()).hasSize(1);
        assertThat(celcoin.documentos().get(0).tipo()).isEqualTo("CCMEI");
        assertThat(celcoin.documentos().get(0).sha256()).isEqualTo("sha-1");
    }

    @Test
    void toCelcoinRequestAceitaListaDeDocumentosNula() {
        RequisicaoKyb req = new RequisicaoKyb(UUID.randomUUID(), UUID.randomUUID(), "11222333000181", "ACME", null);

        CelcoinKybRequest celcoin = mapper.toCelcoinRequest(req);

        assertThat(celcoin.documentos()).isEmpty();
    }

    @Test
    void toRespostaKybMapeiaSituacaoActiveParaAtiva() {
        CelcoinKybResponse celcoin = new CelcoinKybResponse(
                "ACTIVE",
                "ACME Industria LTDA",
                "ACME",
                "62.01-5-01",
                "62.09-1-00",
                new BigDecimal("500000.00"),
                LocalDate.of(2010, 1, 1),
                List.of(new CelcoinKybResponse.RepresentanteLegalCelcoin("Joao", "52998224725", "Diretor")));

        RespostaKyb resp = mapper.toRespostaKyb(celcoin, "{}");

        assertThat(resp.situacaoCadastral()).isEqualTo(SituacaoCadastral.ATIVA);
        assertThat(resp.razaoSocial()).isEqualTo("ACME Industria LTDA");
        assertThat(resp.representantes()).hasSize(1);
        assertThat(resp.representantes().get(0).nome()).isEqualTo("Joao");
        assertThat(resp.payloadProvider()).isEqualTo("{}");
    }

    @Test
    void toRespostaKybMapeiaSituacoesPortugues() {
        CelcoinKybResponse suspensa = new CelcoinKybResponse("SUSPENSA", null, null, null, null, null, null, null);
        CelcoinKybResponse baixada = new CelcoinKybResponse("TERMINATED", null, null, null, null, null, null, null);

        assertThat(mapper.toRespostaKyb(suspensa, "{}").situacaoCadastral()).isEqualTo(SituacaoCadastral.SUSPENSA);
        assertThat(mapper.toRespostaKyb(baixada, "{}").situacaoCadastral()).isEqualTo(SituacaoCadastral.BAIXADA);
    }

    @Test
    void toRespostaKybSituacaoDesconhecidaParaPayloadInvalido() {
        CelcoinKybResponse invalida = new CelcoinKybResponse("XYZ", null, null, null, null, null, null, null);

        RespostaKyb resp = mapper.toRespostaKyb(invalida, "{}");

        assertThat(resp.situacaoCadastral()).isEqualTo(SituacaoCadastral.DESCONHECIDA);
        assertThat(resp.representantes()).isEmpty();
    }

    @Test
    void toRespostaKybResponseNuloRetornaDesconhecidaSemRepresentantes() {
        RespostaKyb resp = mapper.toRespostaKyb(null, "{}");

        assertThat(resp.situacaoCadastral()).isEqualTo(SituacaoCadastral.DESCONHECIDA);
        assertThat(resp.representantes()).isEmpty();
        assertThat(resp.payloadProvider()).isEqualTo("{}");
    }
}
