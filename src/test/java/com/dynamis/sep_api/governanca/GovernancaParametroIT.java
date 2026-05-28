package com.dynamis.sep_api.governanca;

import com.dynamis.sep_api.governanca.application.dto.AlterarParametroCommand;
import com.dynamis.sep_api.governanca.application.query.ParametroOperacionalReader;
import com.dynamis.sep_api.governanca.application.usecase.AlterarParametroOperacionalUseCase;
import com.dynamis.sep_api.governanca.application.usecase.ListarParametrosOperacionaisUseCase;
import com.dynamis.sep_api.governanca.infrastructure.persistence.ParametroOperacionalRepository;
import com.dynamis.sep_api.governanca.infrastructure.persistence.VersaoParametroOperacionalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT do modulo governanca (Sprint 18 Task 18.4): seed carregado, alteracao versiona + audita
 * historico, e a porta de leitura le valor governado com fallback ao default.
 */
@SpringBootTest
@ActiveProfiles("test")
class GovernancaParametroIT {

    @Autowired
    ListarParametrosOperacionaisUseCase listar;

    @Autowired
    AlterarParametroOperacionalUseCase alterar;

    @Autowired
    ParametroOperacionalReader reader;

    @Autowired
    ParametroOperacionalRepository parametroRepository;

    @Autowired
    VersaoParametroOperacionalRepository versaoRepository;

    @Autowired
    Environment environment;

    @org.junit.jupiter.api.BeforeEach
    void guardaBanco() {
        if (!environment.getProperty("spring.datasource.url", "").contains("sep_test")) {
            throw new IllegalStateException("GovernancaParametroIT deve rodar apenas no banco sep_test");
        }
    }

    @Test
    void seedInicialCarregadoEReaderLeValorGovernado() {
        assertThat(listar.executar())
                .extracting("chave")
                .contains(
                        "credito.valor.maximo.pf",
                        "backoffice.webhook.pendente.horas",
                        "credito.open-finance.bonus.entradas.altas",
                        "credito.open-finance.bonus.entradas.minimas",
                        "credito.open-finance.penalidade.saldo.negativo");
        // reader le o valor governado (seed)
        assertThat(reader.lerInteiro("backoffice.webhook.pendente.horas", 999)).isEqualTo(1);
        assertThat(reader.lerDecimal("credito.valor.maximo.pf", BigDecimal.ZERO))
                .isEqualByComparingTo("50000.00");
        assertThat(reader.lerInteiro("credito.open-finance.penalidade.saldo.negativo", 0))
                .isEqualTo(150);
        // chave inexistente -> fallback ao default
        assertThat(reader.lerInteiro("nao.existe", 42)).isEqualTo(42);
    }

    @Test
    void alterarParametroVersionaEGravaHistorico() {
        // Estado pode acumular entre execucoes (sep_test compartilhado, seed nao resetado):
        // asserts relativos ao estado atual + restauracao do valor original.
        var atual =
                parametroRepository.findByChave("credito.score.pre-aprovacao").orElseThrow();
        var parametroId = atual.getId();
        String valorOriginal = atual.getValor();
        int versaoAntes = atual.getVersao();
        int historicoAntes =
                versaoRepository.findByParametroIdOrderByVersaoDesc(parametroId).size();

        var view = alterar.executar(new AlterarParametroCommand(
                "credito.score.pre-aprovacao", "750", "Ajuste de politica de credito", UUID.randomUUID()));

        assertThat(view.versao()).isEqualTo(versaoAntes + 1);
        assertThat(view.valor()).isEqualTo("750");
        assertThat(reader.lerInteiro("credito.score.pre-aprovacao", 0)).isEqualTo(750);

        var historico = versaoRepository.findByParametroIdOrderByVersaoDesc(parametroId);
        assertThat(historico).hasSize(historicoAntes + 1);
        assertThat(historico.get(0).getValorAnterior()).isEqualTo(valorOriginal);
        assertThat(historico.get(0).getValorNovo()).isEqualTo("750");
        assertThat(historico.get(0).getJustificativa()).isEqualTo("Ajuste de politica de credito");

        // restaura valor original para nao impactar outros testes do banco compartilhado
        alterar.executar(new AlterarParametroCommand(
                "credito.score.pre-aprovacao", valorOriginal, "Restauracao pos-teste", UUID.randomUUID()));
    }
}
