package com.dynamis.sep_api.cobranca.domain.model;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenegociacaoTest {

    private static final UUID PARCELA = UUID.randomUUID();
    private static final UUID AGENDA = UUID.randomUUID();
    private static final UUID TOMADOR = UUID.randomUUID();
    private static final UUID FINANCEIRO = UUID.randomUUID();
    private static final OffsetDateTime PROPOSTA_EM = OffsetDateTime.parse("2026-05-25T10:00:00-03:00");

    @Test
    void propor_camposValidos_estadoProposta() {
        Renegociacao r = novaProposta(StatusParcela.ATRASADA);

        assertThat(r.getStatus()).isEqualTo(StatusRenegociacao.PROPOSTA);
        assertThat(r.getStatusParcelaAnterior()).isEqualTo(StatusParcela.ATRASADA);
        assertThat(r.getNovoValorParcela()).isEqualByComparingTo("110.00");
        assertThat(r.getNumeroParcelas()).isEqualTo(3);
        assertThat(r.getDesconto()).isEqualByComparingTo("0.00");
        assertThat(r.getDataExpiracao()).isAfter(r.getDataProposta());
        assertThat(r.getAgendaSubstitutaId()).isNull();
        assertThat(r.getDataDecisao()).isNull();
    }

    @Test
    void propor_statusInvalido_rejeita() {
        assertThatThrownBy(() -> novaProposta(StatusParcela.PENDENTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statusParcelaAnterior");
    }

    @Test
    void propor_valorZero_rejeita() {
        assertThatThrownBy(() -> Renegociacao.propor(
                        PARCELA,
                        AGENDA,
                        TOMADOR,
                        StatusParcela.ATRASADA,
                        BigDecimal.ZERO,
                        LocalDate.of(2026, 7, 10),
                        3,
                        BigDecimal.ZERO,
                        "acordo",
                        FINANCEIRO,
                        PROPOSTA_EM,
                        PROPOSTA_EM.plusDays(7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("novoValorParcela");
    }

    @Test
    void propor_expiracaoNoMesmoInstante_rejeita() {
        assertThatThrownBy(() -> Renegociacao.propor(
                        PARCELA,
                        AGENDA,
                        TOMADOR,
                        StatusParcela.ATRASADA,
                        new BigDecimal("100"),
                        LocalDate.of(2026, 7, 10),
                        2,
                        BigDecimal.ZERO,
                        "acordo",
                        FINANCEIRO,
                        PROPOSTA_EM,
                        PROPOSTA_EM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataExpiracao");
    }

    @Test
    void aceitar_transicionaParaAceita() {
        Renegociacao r = novaProposta(StatusParcela.ATRASADA);
        UUID nova = UUID.randomUUID();

        r.aceitar(nova, PROPOSTA_EM.plusHours(2));

        assertThat(r.getStatus()).isEqualTo(StatusRenegociacao.ACEITA);
        assertThat(r.getAgendaSubstitutaId()).isEqualTo(nova);
        assertThat(r.getDataDecisao()).isEqualTo(PROPOSTA_EM.plusHours(2));
    }

    @Test
    void aceitarDuasVezes_rejeita() {
        Renegociacao r = novaProposta(StatusParcela.ATRASADA);
        r.aceitar(UUID.randomUUID(), PROPOSTA_EM.plusHours(2));

        assertThatThrownBy(() -> r.aceitar(UUID.randomUUID(), PROPOSTA_EM.plusHours(3)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recusar_transicionaParaRecusada() {
        Renegociacao r = novaProposta(StatusParcela.INADIMPLENTE);

        r.recusar(PROPOSTA_EM.plusHours(1));

        assertThat(r.getStatus()).isEqualTo(StatusRenegociacao.RECUSADA);
        assertThat(r.getDataDecisao()).isEqualTo(PROPOSTA_EM.plusHours(1));
        assertThat(r.getAgendaSubstitutaId()).isNull();
    }

    @Test
    void expirar_aposJanela_transicionaParaExpirada() {
        Renegociacao r = novaProposta(StatusParcela.ATRASADA);

        r.expirar(PROPOSTA_EM.plusDays(8));

        assertThat(r.getStatus()).isEqualTo(StatusRenegociacao.EXPIRADA);
    }

    @Test
    void expirouEm_proposta_apos_data_expiracao() {
        Renegociacao r = novaProposta(StatusParcela.ATRASADA);

        assertThat(r.expirouEm(r.getDataExpiracao().minusSeconds(1))).isFalse();
        assertThat(r.expirouEm(r.getDataExpiracao())).isTrue();
    }

    @Test
    void expirouEm_aposDecisao_naoExpiraNovamente() {
        Renegociacao r = novaProposta(StatusParcela.ATRASADA);
        r.recusar(PROPOSTA_EM.plusHours(1));

        assertThat(r.expirouEm(r.getDataExpiracao().plusDays(30))).isFalse();
    }

    private static Renegociacao novaProposta(StatusParcela statusAnterior) {
        return Renegociacao.propor(
                PARCELA,
                AGENDA,
                TOMADOR,
                statusAnterior,
                new BigDecimal("110.00"),
                LocalDate.of(2026, 7, 10),
                3,
                new BigDecimal("0.00"),
                "Renegociacao por dificuldade temporaria",
                FINANCEIRO,
                PROPOSTA_EM,
                PROPOSTA_EM.plusDays(7));
    }
}
