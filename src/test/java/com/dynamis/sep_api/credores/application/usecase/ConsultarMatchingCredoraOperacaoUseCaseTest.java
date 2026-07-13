package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.MatchingCredoraOperacaoView;
import com.dynamis.sep_api.credores.domain.exception.MatchingNaoEncontradoException;
import com.dynamis.sep_api.credores.domain.model.MatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.CriterioMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.infrastructure.persistence.MatchingCredoraOperacaoRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Consulta individual da sugestao de matching (Sprint 30 Task 30.5): leitura simples para
 * financeiro/admin com 404 neutro sem UUID para id desconhecido.
 */
class ConsultarMatchingCredoraOperacaoUseCaseTest {

    private MatchingCredoraOperacaoRepository matchingRepository;
    private ConsultarMatchingCredoraOperacaoUseCase useCase;

    @BeforeEach
    void setup() {
        matchingRepository = mock(MatchingCredoraOperacaoRepository.class);
        useCase = new ConsultarMatchingCredoraOperacaoUseCase(matchingRepository);
    }

    @Test
    void consultaSugestaoExistente() {
        MatchingCredoraOperacao sugestao = MatchingCredoraOperacao.sugerir(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10000.00"),
                List.of(CriterioMatchingCredoraOperacao.CREDORA_ATIVA));
        when(matchingRepository.findById(sugestao.getId())).thenReturn(Optional.of(sugestao));

        MatchingCredoraOperacaoView view = useCase.executar(sugestao.getId());

        assertThat(view.id()).isEqualTo(sugestao.getId());
        assertThat(view.criterios()).containsExactly("CREDORA_ATIVA");
    }

    @Test
    void sugestaoInexistenteRetornaErroNeutroSemUuid() {
        UUID desconhecido = UUID.randomUUID();
        when(matchingRepository.findById(desconhecido)).thenReturn(Optional.empty());

        Throwable erro = catchThrowable(() -> useCase.executar(desconhecido));

        assertThat(erro).isInstanceOf(MatchingNaoEncontradoException.class);
        assertThat(erro.getMessage()).doesNotContain(desconhecido.toString());
    }

    @Test
    void idNuloRetorna400() {
        assertThatThrownBy(() -> useCase.executar(null)).isInstanceOf(ValidacaoException.class);
    }
}
