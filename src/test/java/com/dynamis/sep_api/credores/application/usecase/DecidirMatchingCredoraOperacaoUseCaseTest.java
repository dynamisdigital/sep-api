package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.AcaoDecisaoMatching;
import com.dynamis.sep_api.credores.application.dto.DecidirMatchingCredoraOperacaoCommand;
import com.dynamis.sep_api.credores.application.dto.MatchingCredoraOperacaoView;
import com.dynamis.sep_api.credores.domain.event.MatchingCredoraConfirmadoEvent;
import com.dynamis.sep_api.credores.domain.event.MatchingCredoraRejeitadoEvent;
import com.dynamis.sep_api.credores.domain.exception.MatchingDecisaoConflitanteException;
import com.dynamis.sep_api.credores.domain.exception.MatchingNaoEncontradoException;
import com.dynamis.sep_api.credores.domain.model.MatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.CriterioMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.infrastructure.persistence.MatchingCredoraOperacaoRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Decisao assistida do matching (Sprint 30 Task 30.4): confirmar/rejeitar somente sugestao
 * {@code SUGERIDA} sob lock, 404 neutro sem UUID, 409 em terminal, motivo sanitizado e auditoria
 * terminal unica. A confirmacao NAO cria aporte nem chama escrow/provider — o aporte continua
 * fluxo separado da Sprint 29 (o use case nem depende dessas portas).
 */
class DecidirMatchingCredoraOperacaoUseCaseTest {

    private static final UUID ATOR = UUID.randomUUID();

    private MatchingCredoraOperacaoRepository matchingRepository;
    private ApplicationEventPublisher eventPublisher;
    private DecidirMatchingCredoraOperacaoUseCase useCase;

    private MatchingCredoraOperacao sugestao;

    @BeforeEach
    void setup() {
        matchingRepository = mock(MatchingCredoraOperacaoRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new DecidirMatchingCredoraOperacaoUseCase(matchingRepository, eventPublisher);

        sugestao = MatchingCredoraOperacao.sugerir(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10000.00"),
                List.of(CriterioMatchingCredoraOperacao.CREDORA_ATIVA));
        when(matchingRepository.findByIdForUpdate(sugestao.getId())).thenReturn(Optional.of(sugestao));
    }

    private DecidirMatchingCredoraOperacaoCommand comando(AcaoDecisaoMatching acao, String motivo) {
        return new DecidirMatchingCredoraOperacaoCommand(sugestao.getId(), acao, motivo, ATOR);
    }

    @Test
    void confirmarSugestaoRegistraDecisaoEAudita() {
        MatchingCredoraOperacaoView view = useCase.executar(comando(AcaoDecisaoMatching.CONFIRMAR, "  aderente  "));

        assertThat(view.status()).isEqualTo(StatusMatchingCredoraOperacao.CONFIRMADA);
        assertThat(view.decididaEm()).isNotNull();
        assertThat(sugestao.getDecididoPorUsuarioId()).isEqualTo(ATOR);
        assertThat(sugestao.getMotivoDecisaoSanitizado()).isEqualTo("aderente");

        ArgumentCaptor<MatchingCredoraConfirmadoEvent> evento =
                ArgumentCaptor.forClass(MatchingCredoraConfirmadoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().matchingId()).isEqualTo(sugestao.getId());
        assertThat(evento.getValue().operacaoId()).isEqualTo(sugestao.getOperacaoId());
        assertThat(evento.getValue().empresaCredoraId()).isEqualTo(sugestao.getEmpresaCredoraId());
        assertThat(evento.getValue().motivoSanitizado()).isEqualTo("aderente");
        assertThat(evento.getValue().usuarioId()).isEqualTo(ATOR);
    }

    @Test
    void rejeitarSugestaoRegistraDecisaoEAudita() {
        MatchingCredoraOperacaoView view = useCase.executar(comando(AcaoDecisaoMatching.REJEITAR, "fora do apetite"));

        assertThat(view.status()).isEqualTo(StatusMatchingCredoraOperacao.REJEITADA);
        assertThat(sugestao.getStatus()).isEqualTo(StatusMatchingCredoraOperacao.REJEITADA);

        ArgumentCaptor<MatchingCredoraRejeitadoEvent> evento =
                ArgumentCaptor.forClass(MatchingCredoraRejeitadoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().motivoSanitizado()).isEqualTo("fora do apetite");
        assertThat(evento.getValue().usuarioId()).isEqualTo(ATOR);
    }

    @Test
    void decisaoSemMotivoEhValida() {
        MatchingCredoraOperacaoView view = useCase.executar(comando(AcaoDecisaoMatching.CONFIRMAR, null));

        assertThat(view.status()).isEqualTo(StatusMatchingCredoraOperacao.CONFIRMADA);
        assertThat(sugestao.getMotivoDecisaoSanitizado()).isNull();
    }

    @Test
    void sugestaoInexistenteRetornaErroNeutroSemUuid() {
        UUID desconhecido = UUID.randomUUID();
        when(matchingRepository.findByIdForUpdate(desconhecido)).thenReturn(Optional.empty());

        Throwable erro = catchThrowable(() -> useCase.executar(
                new DecidirMatchingCredoraOperacaoCommand(desconhecido, AcaoDecisaoMatching.CONFIRMAR, null, ATOR)));

        assertThat(erro).isInstanceOf(MatchingNaoEncontradoException.class);
        assertThat(erro.getMessage()).doesNotContain(desconhecido.toString());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void decisaoSobreStatusTerminalRetornaConflito() {
        sugestao.confirmar(UUID.randomUUID(), null);

        Throwable erro = catchThrowable(() -> useCase.executar(comando(AcaoDecisaoMatching.REJEITAR, null)));

        assertThat(erro).isInstanceOf(MatchingDecisaoConflitanteException.class);
        assertThat(sugestao.getStatus()).isEqualTo(StatusMatchingCredoraOperacao.CONFIRMADA);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void decisaoUsaLeituraComLock() {
        useCase.executar(comando(AcaoDecisaoMatching.CONFIRMAR, null));

        verify(matchingRepository).findByIdForUpdate(sugestao.getId());
    }

    @Test
    void comandoInvalidoRetorna400SemTocarRepositorio() {
        assertThatThrownBy(() -> useCase.executar(
                        new DecidirMatchingCredoraOperacaoCommand(null, AcaoDecisaoMatching.CONFIRMAR, null, ATOR)))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() ->
                        useCase.executar(new DecidirMatchingCredoraOperacaoCommand(sugestao.getId(), null, null, ATOR)))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(comando(AcaoDecisaoMatching.CONFIRMAR, "x".repeat(256))))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(new DecidirMatchingCredoraOperacaoCommand(
                        sugestao.getId(), AcaoDecisaoMatching.CONFIRMAR, null, null)))
                .isInstanceOf(ValidacaoException.class);

        verifyNoInteractions(matchingRepository, eventPublisher);
    }

    @Test
    void decisaoNaoCriaAporteNemChamaEscrow() {
        // Garantia estrutural: o use case depende somente do repositorio de matching e do
        // publisher — nenhuma porta de aporte/escrow/provider e acionada na decisao.
        useCase.executar(comando(AcaoDecisaoMatching.CONFIRMAR, null));

        verify(matchingRepository).findByIdForUpdate(sugestao.getId());
        org.mockito.Mockito.verifyNoMoreInteractions(matchingRepository);
    }
}
