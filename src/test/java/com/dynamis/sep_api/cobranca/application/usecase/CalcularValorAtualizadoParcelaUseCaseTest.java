package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.ParcelaAtualizadaResult;
import com.dynamis.sep_api.cobranca.application.port.out.ParcelaCobrancaPort;
import com.dynamis.sep_api.cobranca.application.service.calculo.CalculadoraJurosMora;
import com.dynamis.sep_api.cobranca.application.service.calculo.CalculadoraMulta;
import com.dynamis.sep_api.cobranca.application.service.calculo.ParametrosCobrancaProperties;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalcularValorAtualizadoParcelaUseCaseTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private ParcelaCobrancaPort parcelaPort;
    private CalcularValorAtualizadoParcelaUseCase useCase;

    @BeforeEach
    void setup() {
        parcelaPort = mock(ParcelaCobrancaPort.class);
        Clock clock = Clock.fixed(LocalDate.of(2026, 7, 15).atStartOfDay(SP).toInstant(), SP);
        ParametrosCobrancaProperties props = new ParametrosCobrancaProperties();
        useCase = new CalcularValorAtualizadoParcelaUseCase(
                parcelaPort, new CalculadoraJurosMora(), new CalculadoraMulta(), props, clock);
    }

    @Test
    void pendenteSemAtraso_jurosEmultaZero() {
        ParcelaCobranca p = novaParcela("1000.00", LocalDate.of(2026, 8, 1));
        when(parcelaPort.buscarPorId(p.getId())).thenReturn(Optional.of(p));

        ParcelaAtualizadaResult r = useCase.executar(p.getId());

        assertThat(r.jurosMora()).isEqualByComparingTo("0.00");
        assertThat(r.multa()).isEqualByComparingTo("0.00");
        assertThat(r.valorDevidoAtualizado()).isEqualByComparingTo("1000.00");
        assertThat(r.valorEmAberto()).isEqualByComparingTo("1000.00");
    }

    @Test
    void atrasada30Dias_aplicaJuros1PctEMulta2Pct() {
        ParcelaCobranca p = novaParcela("1000.00", LocalDate.of(2026, 6, 15)); // 30 dias atraso
        when(parcelaPort.buscarPorId(p.getId())).thenReturn(Optional.of(p));

        ParcelaAtualizadaResult r = useCase.executar(p.getId());

        // mora pro-rata 1000 * 0.01 * 30/30 = 10
        assertThat(r.jurosMora()).isEqualByComparingTo("10.00");
        // multa 2% = 20
        assertThat(r.multa()).isEqualByComparingTo("20.00");
        // total atualizado = 1030
        assertThat(r.valorDevidoAtualizado()).isEqualByComparingTo("1030.00");
        assertThat(r.valorEmAberto()).isEqualByComparingTo("1030.00");
    }

    @Test
    void pagaRetornaZeros() {
        ParcelaCobranca p = novaParcela("1000.00", LocalDate.of(2026, 6, 1));
        p.registrarRecebimento(
                new BigDecimal("1000.00"),
                p.valorTotal(),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-x",
                null,
                UUID.randomUUID());
        when(parcelaPort.buscarPorId(p.getId())).thenReturn(Optional.of(p));

        ParcelaAtualizadaResult r = useCase.executar(p.getId());

        assertThat(r.status()).isEqualTo(StatusParcela.PAGA);
        assertThat(r.jurosMora()).isEqualByComparingTo("0.00");
        assertThat(r.multa()).isEqualByComparingTo("0.00");
        assertThat(r.valorEmAberto()).isEqualByComparingTo("0.00");
    }

    @Test
    void parcialmentePagaComAtraso_calculaSobreSaldoNominal() {
        ParcelaCobranca p = novaParcela("1000.00", LocalDate.of(2026, 6, 15));
        // recebimento parcial de 400 ainda dentro do prazo (simulado)
        p.registrarRecebimento(
                new BigDecimal("400.00"),
                p.valorTotal(),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-a",
                null,
                UUID.randomUUID());
        when(parcelaPort.buscarPorId(p.getId())).thenReturn(Optional.of(p));

        ParcelaAtualizadaResult r = useCase.executar(p.getId());

        // saldo nominal = 600; mora 30d = 600 * 0.01 = 6; multa = 600 * 0.02 = 12
        assertThat(r.jurosMora()).isEqualByComparingTo("6.00");
        assertThat(r.multa()).isEqualByComparingTo("12.00");
        // devido bruto = 1000 + 6 + 12 = 1018; em aberto = 1018 - 400 = 618
        assertThat(r.valorDevidoAtualizado()).isEqualByComparingTo("1018.00");
        assertThat(r.valorEmAberto()).isEqualByComparingTo("618.00");
        assertThat(r.totalRecebido()).isEqualByComparingTo("400.00");
    }

    @Test
    void parcelaInexistente_lancaExcecao() {
        UUID id = UUID.randomUUID();
        when(parcelaPort.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(id)).isInstanceOf(ParcelaCobrancaNaoEncontradaException.class);
    }

    private static ParcelaCobranca novaParcela(String valor, LocalDate dataVencimento) {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal(valor)), dataVencimento)));
        return agenda.getParcelas().get(0);
    }

    @SuppressWarnings("unused")
    private static Clock clockFixoAt(LocalDate hoje) {
        return Clock.fixed(Instant.parse(hoje + "T03:00:00Z"), SP);
    }
}
