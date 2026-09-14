package com.dynamis.sep_api.notificacao.application.usecase;

import com.dynamis.sep_api.notificacao.application.exception.NotificacaoNaoEncontradaException;
import com.dynamis.sep_api.notificacao.application.port.out.CentralNotificacoesPort;
import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarcarNotificacaoLidaUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-09-14T13:00:00Z");
    private static final UUID USUARIO = UUID.randomUUID();

    private final CentralNotificacoesPort central = mock(CentralNotificacoesPort.class);
    private final MarcarNotificacaoLidaUseCase useCase =
            new MarcarNotificacaoLidaUseCase(central, Clock.fixed(AGORA, ZoneOffset.UTC));

    private static Notificacao daCentral() {
        return Notificacao.disponibilizarInApp(
                USUARIO,
                new OrigemNotificacao(TipoNotificacao.DESEMBOLSO_PIX_CONCLUIDO, "transferencia-1"),
                new ConteudoNotificacao("Titulo", "Mensagem", null),
                AGORA.minusSeconds(3600).atOffset(ZoneOffset.UTC));
    }

    @Test
    void primeiraMarcacao_gravaALeituraComOInstanteDoRelogio() {
        Notificacao notificacao = daCentral();
        when(central.buscarParaAtualizar(USUARIO, notificacao.getId())).thenReturn(Optional.of(notificacao));

        Notificacao marcada = useCase.marcar(USUARIO, notificacao.getId());

        assertThat(marcada.getLidaEm()).contains(AGORA.atOffset(ZoneOffset.UTC));
        verify(central).registrarLeitura(notificacao);
    }

    @Test
    void leitura_eGravadaNaPrecisaoDoBanco() {
        Notificacao notificacao = daCentral();
        when(central.buscarParaAtualizar(USUARIO, notificacao.getId())).thenReturn(Optional.of(notificacao));
        MarcarNotificacaoLidaUseCase comNanossegundos = new MarcarNotificacaoLidaUseCase(
                central, Clock.fixed(Instant.parse("2026-09-14T13:00:00.123456789Z"), ZoneOffset.UTC));

        Notificacao marcada = comNanossegundos.marcar(USUARIO, notificacao.getId());

        assertThat(marcada.getLidaEm()).contains(OffsetDateTime.parse("2026-09-14T13:00:00.123456Z"));
    }

    @Test
    void remarcacao_naoGravaEPreservaAPrimeiraLeitura() {
        Notificacao notificacao = daCentral();
        OffsetDateTime primeira = AGORA.minusSeconds(60).atOffset(ZoneOffset.UTC);
        notificacao.marcarLida(primeira);
        when(central.buscarParaAtualizar(USUARIO, notificacao.getId())).thenReturn(Optional.of(notificacao));

        Notificacao marcada = useCase.marcar(USUARIO, notificacao.getId());

        assertThat(marcada.getLidaEm()).contains(primeira);
        verify(central, never()).registrarLeitura(notificacao);
    }

    @Test
    void notificacaoQueAPortaNaoDevolve_eO404Neutro() {
        UUID qualquer = UUID.randomUUID();
        when(central.buscarParaAtualizar(USUARIO, qualquer)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.marcar(USUARIO, qualquer))
                .isInstanceOf(NotificacaoNaoEncontradaException.class)
                .extracting("codigo")
                .isEqualTo("NTF-404-001");
    }
}
