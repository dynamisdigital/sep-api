package com.dynamis.sep_api.credores.application.service;

import com.dynamis.sep_api.credores.domain.vo.CriterioMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.StatusCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;
import com.dynamis.sep_api.credores.domain.vo.StatusOperacaoFinanciada;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras de elegibilidade do matching credora-operacao (Sprint 30 Task 30.1). O par avaliado e
 * sempre (credora dona, operacao da propria carteira): o matching sugere que a operacao esta
 * pronta para receber aporte assistido — a decisao continua com financeiro/admin.
 */
class ValidadorElegibilidadeMatchingCredoraOperacaoTest {

    private final ValidadorElegibilidadeMatchingCredoraOperacao validador =
            new ValidadorElegibilidadeMatchingCredoraOperacao();

    private final UUID credoraId = UUID.randomUUID();
    private final UUID operacaoId = UUID.randomUUID();

    private CandidatoMatchingCredoraOperacao candidato(
            StatusCredora statusCredora,
            StatusElegibilidade elegibilidadeCredora,
            BigDecimal capacidadeAporte,
            StatusOperacaoFinanciada statusOperacao,
            String statusContrato,
            BigDecimal valorOperacao,
            boolean parComMatchingExistente) {
        return new CandidatoMatchingCredoraOperacao(
                credoraId,
                operacaoId,
                statusCredora,
                elegibilidadeCredora,
                capacidadeAporte,
                statusOperacao,
                statusContrato,
                valorOperacao,
                parComMatchingExistente);
    }

    private CandidatoMatchingCredoraOperacao candidatoElegivel() {
        return candidato(
                StatusCredora.ATIVA,
                StatusElegibilidade.ELEGIVEL,
                new BigDecimal("50000.00"),
                StatusOperacaoFinanciada.ASSOCIADA,
                "ASSINADO",
                new BigDecimal("10000.00"),
                false);
    }

    @Test
    void candidatoComTodosOsCriterios_eElegivelComCriteriosAtendidos() {
        ResultadoElegibilidadeMatching resultado = validador.avaliar(candidatoElegivel());

        assertThat(resultado.elegivel()).isTrue();
        assertThat(resultado.criterioViolado()).isNull();
        assertThat(resultado.criteriosAtendidos())
                .containsExactly(
                        CriterioMatchingCredoraOperacao.CREDORA_ATIVA,
                        CriterioMatchingCredoraOperacao.CREDORA_ELEGIVEL,
                        CriterioMatchingCredoraOperacao.OPERACAO_ATIVA,
                        CriterioMatchingCredoraOperacao.CONTRATO_ASSINADO,
                        CriterioMatchingCredoraOperacao.VALOR_OPERACAO_DISPONIVEL,
                        CriterioMatchingCredoraOperacao.CAPACIDADE_COMPORTA_VALOR,
                        CriterioMatchingCredoraOperacao.PAR_SEM_MATCHING_PREVIO);
    }

    @Test
    void capacidadeNaoInformada_eElegivelSemOCriterioDeCapacidade() {
        ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato(
                StatusCredora.ATIVA,
                StatusElegibilidade.ELEGIVEL,
                null,
                StatusOperacaoFinanciada.ASSOCIADA,
                "ASSINADO",
                new BigDecimal("10000.00"),
                false));

        assertThat(resultado.elegivel()).isTrue();
        assertThat(resultado.criteriosAtendidos())
                .doesNotContain(CriterioMatchingCredoraOperacao.CAPACIDADE_COMPORTA_VALOR);
    }

    @Test
    void capacidadeIgualAoValorDaOperacao_eElegivel() {
        ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato(
                StatusCredora.ATIVA,
                StatusElegibilidade.ELEGIVEL,
                new BigDecimal("10000.00"),
                StatusOperacaoFinanciada.ASSOCIADA,
                "ASSINADO",
                new BigDecimal("10000.00"),
                false));

        assertThat(resultado.elegivel()).isTrue();
        assertThat(resultado.criteriosAtendidos()).contains(CriterioMatchingCredoraOperacao.CAPACIDADE_COMPORTA_VALOR);
    }

    @Test
    void credoraCadastradaOuSuspensa_naoGeraCandidato() {
        for (StatusCredora status : new StatusCredora[] {StatusCredora.CADASTRADA, StatusCredora.SUSPENSA}) {
            ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato(
                    status,
                    StatusElegibilidade.ELEGIVEL,
                    new BigDecimal("50000.00"),
                    StatusOperacaoFinanciada.ASSOCIADA,
                    "ASSINADO",
                    new BigDecimal("10000.00"),
                    false));

            assertThat(resultado.elegivel()).isFalse();
            assertThat(resultado.criterioViolado()).isEqualTo(CriterioMatchingCredoraOperacao.CREDORA_ATIVA);
        }
    }

    @Test
    void credoraPendenteOuInelegivel_naoGeraCandidato() {
        for (StatusElegibilidade elegibilidade :
                new StatusElegibilidade[] {StatusElegibilidade.PENDENTE, StatusElegibilidade.INELEGIVEL}) {
            ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato(
                    StatusCredora.ATIVA,
                    elegibilidade,
                    new BigDecimal("50000.00"),
                    StatusOperacaoFinanciada.ASSOCIADA,
                    "ASSINADO",
                    new BigDecimal("10000.00"),
                    false));

            assertThat(resultado.elegivel()).isFalse();
            assertThat(resultado.criterioViolado()).isEqualTo(CriterioMatchingCredoraOperacao.CREDORA_ELEGIVEL);
        }
    }

    @Test
    void operacaoEncerrada_naoGeraCandidato() {
        ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato(
                StatusCredora.ATIVA,
                StatusElegibilidade.ELEGIVEL,
                new BigDecimal("50000.00"),
                StatusOperacaoFinanciada.ENCERRADA,
                "ASSINADO",
                new BigDecimal("10000.00"),
                false));

        assertThat(resultado.elegivel()).isFalse();
        assertThat(resultado.criterioViolado()).isEqualTo(CriterioMatchingCredoraOperacao.OPERACAO_ATIVA);
    }

    @Test
    void contratoNaoAssinado_naoGeraCandidato() {
        ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato(
                StatusCredora.ATIVA,
                StatusElegibilidade.ELEGIVEL,
                new BigDecimal("50000.00"),
                StatusOperacaoFinanciada.ASSOCIADA,
                "PENDENTE_ASSINATURA",
                new BigDecimal("10000.00"),
                false));

        assertThat(resultado.elegivel()).isFalse();
        assertThat(resultado.criterioViolado()).isEqualTo(CriterioMatchingCredoraOperacao.CONTRATO_ASSINADO);
    }

    @Test
    void contratoDesconhecido_naoGeraCandidato() {
        ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato(
                StatusCredora.ATIVA,
                StatusElegibilidade.ELEGIVEL,
                new BigDecimal("50000.00"),
                StatusOperacaoFinanciada.ASSOCIADA,
                null,
                new BigDecimal("10000.00"),
                false));

        assertThat(resultado.elegivel()).isFalse();
        assertThat(resultado.criterioViolado()).isEqualTo(CriterioMatchingCredoraOperacao.CONTRATO_ASSINADO);
    }

    @Test
    void valorDaOperacaoAusenteOuNaoPositivo_naoGeraCandidato() {
        for (BigDecimal valor : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("-1.00")}) {
            ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato(
                    StatusCredora.ATIVA,
                    StatusElegibilidade.ELEGIVEL,
                    new BigDecimal("50000.00"),
                    StatusOperacaoFinanciada.ASSOCIADA,
                    "ASSINADO",
                    valor,
                    false));

            assertThat(resultado.elegivel()).isFalse();
            assertThat(resultado.criterioViolado())
                    .isEqualTo(CriterioMatchingCredoraOperacao.VALOR_OPERACAO_DISPONIVEL);
        }
    }

    @Test
    void capacidadeInsuficiente_naoGeraCandidato() {
        ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato(
                StatusCredora.ATIVA,
                StatusElegibilidade.ELEGIVEL,
                new BigDecimal("9999.99"),
                StatusOperacaoFinanciada.ASSOCIADA,
                "ASSINADO",
                new BigDecimal("10000.00"),
                false));

        assertThat(resultado.elegivel()).isFalse();
        assertThat(resultado.criterioViolado()).isEqualTo(CriterioMatchingCredoraOperacao.CAPACIDADE_COMPORTA_VALOR);
    }

    @Test
    void parComMatchingPrevio_naoGeraCandidatoDuplicado() {
        // Qualquer matching previo do par bloqueia nova sugestao — inclusive REJEITADA, para o
        // refresh nao re-sugerir par que o operador acabou de rejeitar.
        ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato(
                StatusCredora.ATIVA,
                StatusElegibilidade.ELEGIVEL,
                new BigDecimal("50000.00"),
                StatusOperacaoFinanciada.ASSOCIADA,
                "ASSINADO",
                new BigDecimal("10000.00"),
                true));

        assertThat(resultado.elegivel()).isFalse();
        assertThat(resultado.criterioViolado()).isEqualTo(CriterioMatchingCredoraOperacao.PAR_SEM_MATCHING_PREVIO);
    }

    @Test
    void candidatoInelegivel_naoCarregaCriteriosAtendidos() {
        ResultadoElegibilidadeMatching resultado = validador.avaliar(candidato(
                StatusCredora.SUSPENSA,
                StatusElegibilidade.ELEGIVEL,
                new BigDecimal("50000.00"),
                StatusOperacaoFinanciada.ASSOCIADA,
                "ASSINADO",
                new BigDecimal("10000.00"),
                false));

        assertThat(resultado.criteriosAtendidos()).isEmpty();
    }
}
