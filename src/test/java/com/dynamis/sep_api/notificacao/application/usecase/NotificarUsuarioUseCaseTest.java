package com.dynamis.sep_api.notificacao.application.usecase;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dynamis.sep_api.notificacao.application.port.out.EnvioEmailPort;
import com.dynamis.sep_api.notificacao.application.port.out.NotificacaoPort;
import com.dynamis.sep_api.notificacao.application.port.out.dto.EmailNotificacao;
import com.dynamis.sep_api.notificacao.application.port.out.dto.ResultadoEnvioEmail;
import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.Entrega;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.SituacaoEntrega;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificarUsuarioUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-14T13:00:00Z"), ZoneOffset.UTC);
    private static final UUID USUARIO = UUID.randomUUID();
    private static final String ENDERECO = "cliente@sep.test";
    private static final OrigemNotificacao BLOQUEIO =
            new OrigemNotificacao(TipoNotificacao.CONTA_BLOQUEADA, "2026-09-14T12:59:00Z");
    private static final OrigemNotificacao DESEMBOLSO = new OrigemNotificacao(
            TipoNotificacao.DESEMBOLSO_PIX_CONCLUIDO, UUID.randomUUID().toString());
    private static final ConteudoNotificacao CONTEUDO =
            new ConteudoNotificacao("Conta SEP bloqueada", "Sua conta esta bloqueada por 30 minutos.", null);

    private final PortEmMemoria port = new PortEmMemoria();
    private final List<EmailNotificacao> enviados = new ArrayList<>();
    private EnvioEmailPort provider = email -> {
        enviados.add(email);
        return ResultadoEnvioEmail.SIMULADO;
    };

    private final Logger logger = (Logger) LoggerFactory.getLogger(NotificarUsuarioUseCase.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void capturarLog() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void soltarLog() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private NotificarUsuarioUseCase useCase() {
        return new NotificarUsuarioUseCase(port, email -> provider.enviar(email), CLOCK);
    }

    @Test
    void central_gravaDisponivelSemEnviarEmail() {
        useCase().disponibilizarNaCentral(USUARIO, DESEMBOLSO, CONTEUDO);

        assertThat(port.entregasPersistidas())
                .containsExactly(Entrega.inAppDisponivel(CLOCK.instant().atOffset(ZoneOffset.UTC)));
        assertThat(enviados).isEmpty();
    }

    @Test
    void instantes_saoGravadosNaPrecisaoDoBanco() {
        NotificarUsuarioUseCase comNanossegundos = new NotificarUsuarioUseCase(
                port, provider, Clock.fixed(Instant.parse("2026-09-14T13:00:00.123456789Z"), ZoneOffset.UTC));

        comNanossegundos.disponibilizarNaCentral(USUARIO, DESEMBOLSO, CONTEUDO);

        assertThat(port.entregasPersistidas())
                .extracting(Entrega::atualizadaEm)
                .containsExactly(java.time.OffsetDateTime.parse("2026-09-14T13:00:00.123456Z"));
    }

    @Test
    void central_repetida_naoGravaSegunda() {
        useCase().disponibilizarNaCentral(USUARIO, DESEMBOLSO, CONTEUDO);
        useCase().disponibilizarNaCentral(USUARIO, DESEMBOLSO, CONTEUDO);

        assertThat(port.entregasPersistidas()).hasSize(1);
    }

    @Test
    void email_registraPendenteAntesDeEnviarEGravaASimulacao() {
        provider = email -> {
            assertThat(port.entregasPersistidas())
                    .extracting(Entrega::situacao)
                    .as("a tentativa ja esta gravada quando o provider e chamado")
                    .containsExactly(SituacaoEntrega.PENDENTE);
            enviados.add(email);
            return ResultadoEnvioEmail.SIMULADO;
        };

        useCase().enviarEmail(USUARIO, ENDERECO, BLOQUEIO, CONTEUDO);

        assertThat(enviados).containsExactly(new EmailNotificacao(ENDERECO, CONTEUDO.titulo(), CONTEUDO.mensagem()));
        assertThat(port.entregasPersistidas())
                .extracting(Entrega::canal, Entrega::situacao)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(CanalNotificacao.EMAIL, SituacaoEntrega.SIMULADA));
    }

    @Test
    void email_aceitoPeloProvider_gravaEnviada() {
        provider = email -> ResultadoEnvioEmail.ENVIADO;

        useCase().enviarEmail(USUARIO, ENDERECO, BLOQUEIO, CONTEUDO);

        assertThat(port.entregasPersistidas()).extracting(Entrega::situacao).containsExactly(SituacaoEntrega.ENVIADA);
    }

    @Test
    void email_providerLanca_gravaFalhouComNomeDaClasseSemPropagar() {
        provider = email -> {
            throw new IllegalStateException("SMTP recusou " + ENDERECO + ": " + CONTEUDO.mensagem());
        };

        useCase().enviarEmail(USUARIO, ENDERECO, BLOQUEIO, CONTEUDO);

        assertThat(port.entregasPersistidas()).singleElement().satisfies(entrega -> {
            assertThat(entrega.situacao()).isEqualTo(SituacaoEntrega.FALHOU);
            assertThat(entrega.motivoFalha()).isEqualTo("java.lang.IllegalStateException");
        });
    }

    @Test
    void email_falhaNoProvider_logSemEnderecoCorpoNemMensagemDaExcecao() {
        provider = email -> {
            throw new IllegalStateException("SMTP recusou " + ENDERECO + ": " + CONTEUDO.mensagem());
        };

        useCase().enviarEmail(USUARIO, ENDERECO, BLOQUEIO, CONTEUDO);

        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list)
                .allSatisfy(evento -> assertThat(evento.getFormattedMessage() + evento.getKeyValuePairs())
                        .doesNotContain(ENDERECO, CONTEUDO.mensagem(), CONTEUDO.titulo(), "SMTP recusou"));
        assertThat(appender.list)
                .allSatisfy(evento -> assertThat(evento.getThrowableProxy()).isNull());
    }

    @Test
    void email_repetidoParaAMesmaOrigem_naoChamaOProvider() {
        useCase().enviarEmail(USUARIO, ENDERECO, BLOQUEIO, CONTEUDO);
        useCase().enviarEmail(USUARIO, ENDERECO, BLOQUEIO, CONTEUDO);

        assertThat(enviados).hasSize(1);
        assertThat(port.entregasPersistidas()).hasSize(1);
    }

    @Test
    void email_depoisDeFalha_eTentadoDeNovo() {
        provider = email -> {
            throw new IllegalStateException("fora do ar");
        };
        useCase().enviarEmail(USUARIO, ENDERECO, BLOQUEIO, CONTEUDO);
        provider = email -> ResultadoEnvioEmail.ENVIADO;

        useCase().enviarEmail(USUARIO, ENDERECO, BLOQUEIO, CONTEUDO);

        assertThat(port.entregasPersistidas())
                .extracting(Entrega::situacao)
                .containsExactly(SituacaoEntrega.FALHOU, SituacaoEntrega.ENVIADA);
    }

    @Test
    void email_semEndereco_naoRegistraTentativa() {
        assertThatThrownBy(() -> useCase().enviarEmail(USUARIO, " ", BLOQUEIO, CONTEUDO))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(port.entregasPersistidas()).isEmpty();
    }

    @Test
    void falhaDePersistencia_sobeParaQuemChama() {
        NotificacaoPort quebrado = new PortEmMemoria() {
            @Override
            public boolean registrarSeInedita(Notificacao notificacao) {
                throw new IllegalStateException("banco fora");
            }
        };
        NotificarUsuarioUseCase useCase = new NotificarUsuarioUseCase(quebrado, provider, CLOCK);

        assertThatThrownBy(() -> useCase.disponibilizarNaCentral(USUARIO, DESEMBOLSO, CONTEUDO))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Guarda uma copia da {@link Entrega} a cada gravacao, com a semantica da chave de origem: o que
     * fica aqui e o que foi persistido, nao o estado em memoria do agregado.
     */
    private static class PortEmMemoria implements NotificacaoPort {

        private final Map<UUID, Notificacao> registradas = new LinkedHashMap<>();
        private final Map<UUID, Entrega> persistidas = new LinkedHashMap<>();

        @Override
        public boolean registrarSeInedita(Notificacao notificacao) {
            boolean jaExiste = registradas.values().stream()
                    .anyMatch(existente -> existente.getOrigem().equals(notificacao.getOrigem())
                            && existente.getUsuarioId().equals(notificacao.getUsuarioId())
                            && persistidas.get(existente.getId()).situacao() != SituacaoEntrega.FALHOU);
            if (jaExiste) {
                return false;
            }
            registradas.put(notificacao.getId(), notificacao);
            persistidas.put(notificacao.getId(), notificacao.getEntrega());
            return true;
        }

        @Override
        public void atualizarEntrega(Notificacao notificacao) {
            persistidas.put(notificacao.getId(), notificacao.getEntrega());
        }

        List<Entrega> entregasPersistidas() {
            return List.copyOf(persistidas.values());
        }
    }
}
