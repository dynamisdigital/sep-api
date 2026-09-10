package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.dto.RegistrarParecerCommand;
import com.dynamis.sep_api.credito.domain.exception.StatusPropostaInvalidoException;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.DecisaoCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ParecerCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Parecer sobre proposta em estado final e recusado pelo status, e nao pelo dado: sai com o codigo
 * de {@link StatusPropostaInvalidoException} e com a mensagem que o endpoint sempre devolveu.
 */
class RegistrarParecerUseCaseTest {

    @Test
    void propostaEmEstadoFinalRecusaNovoParecerPeloStatus() {
        PropostaCreditoRepository propostas = mock(PropostaCreditoRepository.class);
        ParecerCreditoRepository pareceres = mock(ParecerCreditoRepository.class);
        RegistrarParecerUseCase uc = new RegistrarParecerUseCase(
                propostas,
                pareceres,
                mock(ScoreInternoRepository.class),
                mock(DecisaoCreditoRepository.class),
                mock(ApplicationEventPublisher.class));
        PropostaCredito proposta = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12);
        proposta.registrarDecisaoManual(DecisaoParecer.REJEITAR);
        when(propostas.findByIdForUpdate(proposta.getId())).thenReturn(Optional.of(proposta));

        assertThatThrownBy(() -> uc.executar(new RegistrarParecerCommand(
                        proposta.getId(), UUID.randomUUID(), DecisaoParecer.APROVAR, "justificativa suficiente")))
                .isInstanceOf(StatusPropostaInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PRP-400-002")
                .hasMessage("Proposta ja esta em estado final REJEITADA; novo parecer nao permitido");

        verify(pareceres, never()).save(any());
    }
}
