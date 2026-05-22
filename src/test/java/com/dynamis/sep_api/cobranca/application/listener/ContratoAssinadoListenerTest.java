package com.dynamis.sep_api.cobranca.application.listener;

import com.dynamis.sep_api.cobranca.application.dto.GerarAgendaPagamentoCommand;
import com.dynamis.sep_api.cobranca.application.service.calculo.ParametrosCobrancaProperties;
import com.dynamis.sep_api.cobranca.application.service.calculo.SistemaAmortizacao;
import com.dynamis.sep_api.cobranca.application.usecase.GerarAgendaPagamentoUseCase;
import com.dynamis.sep_api.contratos.domain.event.ContratoAssinadoEvent;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContratoAssinadoListenerTest {

    private GerarAgendaPagamentoUseCase useCase;
    private PropostaCreditoRepository propostaRepository;
    private ParametrosCobrancaProperties properties;
    private ContratoAssinadoListener listener;

    @BeforeEach
    void setup() {
        useCase = mock(GerarAgendaPagamentoUseCase.class);
        propostaRepository = mock(PropostaCreditoRepository.class);
        properties = new ParametrosCobrancaProperties();
        listener = new ContratoAssinadoListener(useCase, propostaRepository, properties);
    }

    @Test
    void aoAssinar_invocaUseCaseComDadosDaProposta() {
        UUID contratoId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        OffsetDateTime dataAssinatura = OffsetDateTime.parse("2026-05-01T10:00:00-03:00");
        PropostaCredito proposta =
                PropostaCredito.criar(tomadorId, UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12);
        when(propostaRepository.findById(propostaId)).thenReturn(Optional.of(proposta));

        listener.aoAssinar(new ContratoAssinadoEvent(
                contratoId,
                propostaId,
                tomadorId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "clicksign",
                "env-x",
                "abcdef",
                dataAssinatura));

        ArgumentCaptor<GerarAgendaPagamentoCommand> captor = ArgumentCaptor.forClass(GerarAgendaPagamentoCommand.class);
        verify(useCase).executar(captor.capture());
        GerarAgendaPagamentoCommand cmd = captor.getValue();
        assertThat(cmd.contratoId()).isEqualTo(contratoId);
        assertThat(cmd.propostaId()).isEqualTo(propostaId);
        assertThat(cmd.tomadorId()).isEqualTo(tomadorId);
        assertThat(cmd.valorFinanciado()).isEqualByComparingTo("10000");
        assertThat(cmd.numeroParcelas()).isEqualTo(12);
        assertThat(cmd.taxaMensal()).isEqualByComparingTo(properties.getTaxaJurosMensalDefault());
        assertThat(cmd.dataBase()).isEqualTo(dataAssinatura.toLocalDate());
        assertThat(cmd.sistema()).isEqualTo(SistemaAmortizacao.PRICE);
    }

    @Test
    void propostaInexistente_loga_nao_relanca() {
        UUID propostaId = UUID.randomUUID();
        when(propostaRepository.findById(propostaId)).thenReturn(Optional.empty());

        listener.aoAssinar(new ContratoAssinadoEvent(
                UUID.randomUUID(),
                propostaId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "clicksign",
                "env-x",
                "abcdef",
                OffsetDateTime.now()));

        verify(useCase, never()).executar(any());
    }

    @Test
    void useCaseLancaExcecao_loga_nao_relanca() {
        UUID propostaId = UUID.randomUUID();
        PropostaCredito proposta = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12);
        when(propostaRepository.findById(propostaId)).thenReturn(Optional.of(proposta));
        when(useCase.executar(any())).thenThrow(new RuntimeException("falha simulada"));

        listener.aoAssinar(new ContratoAssinadoEvent(
                UUID.randomUUID(),
                propostaId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "clicksign",
                "env-x",
                "abcdef",
                OffsetDateTime.now()));
        // Sem assertThatThrownBy — relancamento aqui desfaria nada (REQUIRES_NEW), mas regra eh
        // engolir a excecao pra trilha de assinatura nao falhar em cascata.
    }
}
