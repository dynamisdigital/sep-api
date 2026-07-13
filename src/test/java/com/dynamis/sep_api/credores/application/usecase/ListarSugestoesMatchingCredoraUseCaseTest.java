package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.MatchingCredoraOperacaoView;
import com.dynamis.sep_api.credores.domain.model.MatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.CriterioMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.infrastructure.persistence.MatchingCredoraOperacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Listagem operacional de sugestoes de matching (Sprint 30 Task 30.3): somente {@code SUGERIDA},
 * ordenacao delegada ao repositorio (valor elegivel desc, criacao asc) e view sem campos internos.
 */
class ListarSugestoesMatchingCredoraUseCaseTest {

    private MatchingCredoraOperacaoRepository matchingRepository;
    private ListarSugestoesMatchingCredoraUseCase useCase;

    @BeforeEach
    void setup() {
        matchingRepository = mock(MatchingCredoraOperacaoRepository.class);
        useCase = new ListarSugestoesMatchingCredoraUseCase(matchingRepository);
    }

    @Test
    void listaSugeridasComViewMapeada() {
        MatchingCredoraOperacao sugestao = MatchingCredoraOperacao.sugerir(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10000.00"),
                List.of(
                        CriterioMatchingCredoraOperacao.CREDORA_ATIVA,
                        CriterioMatchingCredoraOperacao.CONTRATO_ASSINADO));
        when(matchingRepository.findAllByStatusOrderByValorElegivelDescDataCriacaoAsc(
                        StatusMatchingCredoraOperacao.SUGERIDA))
                .thenReturn(List.of(sugestao));

        List<MatchingCredoraOperacaoView> views = useCase.executar();

        assertThat(views).hasSize(1);
        MatchingCredoraOperacaoView view = views.get(0);
        assertThat(view.id()).isEqualTo(sugestao.getId());
        assertThat(view.empresaCredoraId()).isEqualTo(sugestao.getEmpresaCredoraId());
        assertThat(view.operacaoId()).isEqualTo(sugestao.getOperacaoId());
        assertThat(view.status()).isEqualTo(StatusMatchingCredoraOperacao.SUGERIDA);
        assertThat(view.valorElegivel()).isEqualByComparingTo("10000.00");
        assertThat(view.criterios()).containsExactly("CREDORA_ATIVA", "CONTRATO_ASSINADO");
        assertThat(view.decididaEm()).isNull();

        verify(matchingRepository)
                .findAllByStatusOrderByValorElegivelDescDataCriacaoAsc(StatusMatchingCredoraOperacao.SUGERIDA);
    }

    @Test
    void semSugestoesRetornaListaVazia() {
        when(matchingRepository.findAllByStatusOrderByValorElegivelDescDataCriacaoAsc(
                        StatusMatchingCredoraOperacao.SUGERIDA))
                .thenReturn(List.of());

        assertThat(useCase.executar()).isEmpty();
    }
}
