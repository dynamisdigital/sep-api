package com.dynamis.sep_api.contratos.web.mapper;

import com.dynamis.sep_api.contratos.application.usecase.ConsultarStatusAssinaturaUseCase.StatusAssinaturaContrato;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.web.dto.ContratoResponse;
import com.dynamis.sep_api.contratos.web.dto.StatusAssinaturaResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O mapeamento dos enums de contrato para o DTO web (Sprint 34 Task 34.7).
 *
 * <p>Existe porque a Task 34.6 removeu tres {@code .name()} daqui e <b>nada os executava</b>: o
 * mapper e {@code @MockBean} tanto no {@code ContratoControllerTest} quanto no
 * {@code ContratoAssinaturaControllerTest}, e so {@code toResponse().status()} tinha cobertura, e
 * ainda assim indireta, pela {@code ContratoIT}. {@code toStatusAssinaturaResponse} podia ser
 * reescrito para devolver nulos com a suite inteira verde.
 *
 * <p>Todos os metodos da interface sao {@code default}, entao a implementacao anonima exercita o
 * codigo real sem depender do gerado pelo MapStruct.
 */
class ContratoWebMapperTest {

    private final ContratoWebMapper mapper = new ContratoWebMapper() {};

    @Test
    void toResponseLevaTipoEStatusComoEnumDoDominio() {
        Contrato contrato = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.MUTUO);

        ContratoResponse response = mapper.toResponse(contrato, null);

        assertThat(response.tipo()).isEqualTo(TipoContrato.MUTUO);
        assertThat(response.status()).isEqualTo(contrato.getStatus());
        assertThat(response.propostaId()).isEqualTo(contrato.getPropostaId());
        assertThat(response.tomadorId()).isEqualTo(contrato.getTomadorId());
    }

    @Test
    void toStatusAssinaturaResponseLevaOsDoisEnums() {
        OffsetDateTime atualizacao = OffsetDateTime.parse("2026-08-03T10:00:00-03:00");
        StatusAssinaturaContrato snapshot = new StatusAssinaturaContrato(
                StatusFormalizacao.EM_ASSINATURA, StatusEnvelope.VISUALIZADO, "ext-1", atualizacao);

        StatusAssinaturaResponse response = mapper.toStatusAssinaturaResponse(snapshot);

        assertThat(response.statusContrato()).isEqualTo(StatusFormalizacao.EM_ASSINATURA);
        assertThat(response.statusEnvelope()).isEqualTo(StatusEnvelope.VISUALIZADO);
        assertThat(response.idEnvelopeExterno()).isEqualTo("ext-1");
        assertThat(response.dataAtualizacaoProvider()).isEqualTo(atualizacao);
    }

    /**
     * Envelope ausente e estado normal ate o disparo da assinatura; a Task 34.6 trocou o ternario de
     * null-guard por passagem direta, e o comportamento nao pode ter mudado junto.
     */
    @Test
    void toStatusAssinaturaResponsePreservaEnvelopeAusente() {
        StatusAssinaturaContrato semEnvelope =
                new StatusAssinaturaContrato(StatusFormalizacao.ACEITO, null, null, null);

        StatusAssinaturaResponse response = mapper.toStatusAssinaturaResponse(semEnvelope);

        assertThat(response.statusContrato()).isEqualTo(StatusFormalizacao.ACEITO);
        assertThat(response.statusEnvelope()).isNull();
        assertThat(response.idEnvelopeExterno()).isNull();
    }
}
