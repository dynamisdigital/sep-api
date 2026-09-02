package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.application.exception.ContaBloqueadaException;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
import com.dynamis.sep_api.identity.infrastructure.persistence.LoginAttemptRepository;
import com.dynamis.sep_api.identity.infrastructure.security.LockoutProperties;
import com.dynamis.sep_api.shared.audit.AuditLogSeguranca;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.shared.email.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LockoutServiceTest {

    private static final Instant INSTANTE_BASE = Instant.parse("2026-09-02T12:00:00Z");

    private LoginAttemptRepository repository;
    private AuditLogSegurancaRepository auditRepository;
    private EmailService emailService;
    private LockoutProperties properties;
    private LockoutService service;
    private RelogioAjustavel relogio;

    @BeforeEach
    void setup() {
        repository = mock(LoginAttemptRepository.class);
        auditRepository = mock(AuditLogSegurancaRepository.class);
        emailService = mock(EmailService.class);
        properties = new LockoutProperties();
        relogio = new RelogioAjustavel(INSTANTE_BASE, ZoneId.of("America/Sao_Paulo"));
        service =
                new LockoutService(repository, auditRepository, properties, emailService, new ObjectMapper(), relogio);
    }

    /**
     * Mesmo defeito do {@code RegistrarTentativaLoginUseCase}: o {@code username} vem do corpo da
     * request e a coluna e {@code jsonb}. Concatenar deixava um username com aspas produzir JSON
     * invalido, que o Postgres rejeita — e o audit de LOCKOUT se perderia inteiro.
     */
    @Test
    void detalhesDoLockoutSaoJsonValidoParaUsernameComAspas() throws Exception {
        String hostil = "\"a\\b\"@sep.test";
        when(repository.buscarInstantesDeFalha(eq(hostil), anyList(), any(), any()))
                .thenReturn(falhasRecentes(properties.getMaxAttempts(), Duration.ofMinutes(2)));

        service.avaliarPosFalha(UUID.randomUUID(), hostil);

        ArgumentCaptor<AuditLogSeguranca> captor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(auditRepository).save(captor.capture());
        JsonNode json = new ObjectMapper().readTree(captor.getValue().getDetalhes());
        assertThat(json.get("username").asText()).isEqualTo(hostil);
        assertThat(json.get("lockoutMinutes").asInt()).isEqualTo(properties.getLockoutMinutes());
    }

    /** Falhas a cada {@code intervalo}, terminando agora (ordem decrescente, como o repository). */
    /**
     * <b>Teste que so o {@code Clock} injetado viabiliza</b> (Sprint 35 Task 35.6): a mesma conta,
     * com o mesmo historico de falhas, atravessa o fim do bloqueio sem que nada alem do relogio se
     * mova. Antes o service lia {@code OffsetDateTime.now()} direto, e a unica forma de observar a
     * expiracao era esperar 30 minutos ou reescrever o historico para fingir que o tempo passou —
     * que testa o calculo do teste, nao o do service.
     *
     * <p>Injetar {@code Clock} sem escrever este teste seria trocar acoplamento por cerimonia; e
     * por ele que a injecao se paga.
     */
    @Test
    void bloqueioExpiraQuandoORelogioAvanca() {
        List<OffsetDateTime> falhas =
                falhasAte(properties.getMaxAttempts(), OffsetDateTime.now(relogio), Duration.ofMinutes(1));
        when(repository.buscarInstantesDeFalha(eq("u@sep.test"), anyList(), any(), any()))
                .thenReturn(falhas);

        assertThatThrownBy(() -> service.verificar("u@sep.test"))
                .as("no instante do bloqueio a conta esta barrada")
                .isInstanceOf(ContaBloqueadaException.class);

        relogio.avancar(Duration.ofMinutes(properties.getLockoutMinutes()).minusMinutes(1));
        assertThatThrownBy(() -> service.verificar("u@sep.test"))
                .as("um minuto antes do fim ainda esta barrada")
                .isInstanceOf(ContaBloqueadaException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ContaBloqueadaException.class))
                .extracting(ContaBloqueadaException::getTempoRestante)
                .isEqualTo(Duration.ofMinutes(1));

        relogio.avancar(Duration.ofMinutes(2));
        assertThatNoException()
                .as("passado o bloqueio, o MESMO historico deixa de barrar — so o relogio mudou")
                .isThrownBy(() -> service.verificar("u@sep.test"));
    }

    /** Relogio mutavel: o service guarda a referencia, entao trocar o {@code Clock} nao serviria. */
    private static final class RelogioAjustavel extends Clock {

        private Instant instante;
        private final ZoneId zona;

        private RelogioAjustavel(Instant instante, ZoneId zona) {
            this.instante = instante;
            this.zona = zona;
        }

        void avancar(Duration duracao) {
            instante = instante.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return zona;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return new RelogioAjustavel(instante, zona);
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }

    /**
     * Falhas ancoradas no <b>relogio do service</b>, e nao no relogio real. Depois da Task 35.6 os
     * dois deixariam de concordar sobre "agora", e o historico nasceria no futuro em relacao ao que
     * o service enxerga.
     */
    private List<OffsetDateTime> falhasRecentes(int quantidade, Duration intervalo) {
        return falhasAte(quantidade, OffsetDateTime.now(relogio), intervalo);
    }

    /** Falhas a cada {@code intervalo}, terminando em {@code maisRecente} (ordem decrescente). */
    private static List<OffsetDateTime> falhasAte(int quantidade, OffsetDateTime maisRecente, Duration intervalo) {
        return IntStream.range(0, quantidade)
                .mapToObj(i -> maisRecente.minus(intervalo.multipliedBy(i)))
                .toList();
    }

    @Test
    void verificarPassaQuandoFalhasAbaixoDoLimite() {
        when(repository.buscarInstantesDeFalha(eq("u@sep.test"), anyList(), any(), any()))
                .thenReturn(falhasRecentes(2, Duration.ofMinutes(1)));

        assertThatNoException().isThrownBy(() -> service.verificar("u@sep.test"));
    }

    @Test
    void verificarLancaQuandoAsFalhasFecharamAJanela() {
        when(repository.buscarInstantesDeFalha(eq("locked@sep.test"), anyList(), any(), any()))
                .thenReturn(falhasRecentes(properties.getMaxAttempts(), Duration.ofMinutes(2)));

        assertThatThrownBy(() -> service.verificar("locked@sep.test"))
                .isInstanceOf(ContaBloqueadaException.class)
                .hasMessageContaining("30");
    }

    /**
     * O {@code 423} carrega o tempo <b>restante</b>, nao a duracao configurada (Sprint 34 Task
     * 34.3). Aqui a janela fechou ha 20 minutos, entao restam ~10 dos 30 — devolver a duracao
     * configurada deixa este teste vermelho, e era esse o comportamento ate a Task 34.3.
     */
    @Test
    void verificarCarregaOTempoRestanteDoBloqueioENaoADuracaoConfigurada() {
        OffsetDateTime evento = OffsetDateTime.now(relogio).minusMinutes(20);
        when(repository.buscarInstantesDeFalha(eq("locked@sep.test"), anyList(), any(), any()))
                .thenReturn(falhasAte(properties.getMaxAttempts(), evento, Duration.ofMinutes(1)));

        assertThatThrownBy(() -> service.verificar("locked@sep.test"))
                .isInstanceOfSatisfying(ContaBloqueadaException.class, ex -> assertThat(ex.getTempoRestante())
                        .as("restam exatos 10 dos %d minutos configurados", properties.getLockoutMinutes())
                        .isEqualTo(Duration.ofMinutes(10)));
    }

    /**
     * Regressao da Sprint 33: com a contagem antiga (janela de 30 min) este caso bloqueava, embora
     * nunca tenha havido {@code maxAttempts} falhas dentro dos 15 minutos de deteccao.
     */
    @Test
    void verificarPassaQuandoAsFalhasEstaoEspalhadasAlemDaJanela() {
        when(repository.buscarInstantesDeFalha(eq("espalhado@sep.test"), anyList(), any(), any()))
                .thenReturn(falhasRecentes(properties.getMaxAttempts(), Duration.ofMinutes(5)));

        assertThatNoException().isThrownBy(() -> service.verificar("espalhado@sep.test"));
    }

    @Test
    void verificarLeAJanelaDeDeteccaoMaisADuracaoDoBloqueio() {
        when(repository.buscarInstantesDeFalha(any(), anyList(), any(), any())).thenReturn(List.of());
        Duration janelaEsperada = Duration.ofMinutes(properties.getWindowMinutes() + properties.getLockoutMinutes());

        service.verificar("u@sep.test");

        ArgumentCaptor<OffsetDateTime> inicio = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).buscarInstantesDeFalha(eq("u@sep.test"), anyList(), inicio.capture(), any());
        // Ate a Task 35.6 isto era um isBetween entre dois `now()` reais, porque o service lia o
        // relogio do sistema e a asserção so podia cercar o instante. Com o Clock injetado a
        // igualdade e exata — e um erro de 1ms na janela deixaria de passar despercebido.
        assertThat(inicio.getValue()).isEqualTo(OffsetDateTime.now(relogio).minus(janelaEsperada));
    }

    /**
     * O {@code Pageable} e limite defensivo, nao paginacao. Sem esta asserção um
     * {@code PageRequest.of(1, ...)} faria a query pular as falhas mais recentes e desligaria o
     * lockout do sistema inteiro com a suite verde (achado do code review da Task 33.1).
     */
    @Test
    void verificarLeAPrimeiraPaginaLimitadaDeFalhas() {
        when(repository.buscarInstantesDeFalha(any(), anyList(), any(), any())).thenReturn(List.of());

        service.verificar("u@sep.test");

        ArgumentCaptor<Pageable> limite = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).buscarInstantesDeFalha(eq("u@sep.test"), anyList(), any(), limite.capture());
        assertThat(limite.getValue().getPageNumber()).isZero();
        assertThat(limite.getValue().getPageSize()).isGreaterThanOrEqualTo(properties.getMaxAttempts());
    }

    /**
     * O teto de leitura sai da politica configurada, nao de uma constante (Sprint 34 Task 34.1).
     * Falha se alguem voltar a fixar o valor: com bloqueio mais longo o servico precisa pedir mais
     * historico.
     */
    @Test
    void limiteDeLeituraVemDaConfiguracaoENaoDeUmaConstante() {
        when(repository.buscarInstantesDeFalha(any(), anyList(), any(), any())).thenReturn(List.of());

        service.verificar("u@sep.test");
        properties.setLockoutMinutes(properties.getLockoutMinutes() * 4);
        service.verificar("u@sep.test");

        ArgumentCaptor<Pageable> limites = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, times(2)).buscarInstantesDeFalha(eq("u@sep.test"), anyList(), any(), limites.capture());
        assertThat(limites.getAllValues().get(1).getPageSize())
                .isGreaterThan(limites.getAllValues().get(0).getPageSize());
    }

    @Test
    void avaliarPosFalhaEmiteEmailEAuditQuandoAFalhaAtualBloqueia() {
        UUID usuarioId = UUID.randomUUID();
        when(repository.buscarInstantesDeFalha(eq("u@sep.test"), anyList(), any(), any()))
                .thenReturn(falhasRecentes(properties.getMaxAttempts(), Duration.ofMinutes(2)));

        service.avaliarPosFalha(usuarioId, "u@sep.test");

        ArgumentCaptor<AuditLogSeguranca> captor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoEventoSeguranca.LOCKOUT);
        verify(emailService).enviar(eq("u@sep.test"), any(), any());
    }

    @Test
    void avaliarPosFalhaNaoEmiteQuandoAbaixoDoLimite() {
        when(repository.buscarInstantesDeFalha(any(), anyList(), any(), any()))
                .thenReturn(falhasRecentes(properties.getMaxAttempts() - 1, Duration.ofMinutes(2)));

        service.avaliarPosFalha(UUID.randomUUID(), "u@sep.test");

        verify(auditRepository, never()).save(any());
        verify(emailService, never()).enviar(any(), any(), any());
    }

    /**
     * Regressao da Sprint 33: a condicao antiga era {@code falhasJanela == maxAttempts}, entao duas
     * falhas concorrentes que levassem o contador de 4 para 6 pulavam a igualdade e o bloqueio
     * ficava sem audit e sem email.
     */
    @Test
    void avaliarPosFalhaEmiteQuandoOContadorSaltaAlemDoLimite() {
        when(repository.buscarInstantesDeFalha(any(), anyList(), any(), any()))
                .thenReturn(falhasRecentes(properties.getMaxAttempts() + 1, Duration.ofMinutes(2)));

        service.avaliarPosFalha(UUID.randomUUID(), "u@sep.test");

        verify(auditRepository).save(any());
        verify(emailService).enviar(eq("u@sep.test"), any(), any());
    }

    /**
     * A guarda de transicao nao e vazia: quando o bloqueio vigente vem de um cluster anterior e nao
     * da falha recem-registrada, nada e reemitido.
     */
    @Test
    void avaliarPosFalhaNaoReemiteQuandoOBloqueioVemDeUmClusterAnterior() {
        OffsetDateTime agora = OffsetDateTime.now();
        List<OffsetDateTime> historico = new ArrayList<>();
        historico.add(agora);
        IntStream.range(0, properties.getMaxAttempts())
                .mapToObj(i -> agora.minusMinutes(20).minusMinutes(i))
                .forEach(historico::add);
        when(repository.buscarInstantesDeFalha(any(), anyList(), any(), any())).thenReturn(historico);

        service.avaliarPosFalha(UUID.randomUUID(), "u@sep.test");

        verify(auditRepository, never()).save(any());
        verify(emailService, never()).enviar(any(), any(), any());
    }

    /**
     * {@code CONTA_BLOQUEADA} nao pode contar como falha. Desde a Sprint 34 os use cases
     * <b>registram</b> a tentativa barrada, entao a hipotese deixou de ser teorica: se o status
     * entrasse nesta lista, cada tentativa durante o bloqueio renovaria o proprio bloqueio.
     */
    @Test
    void contagemDeFalhasIgnoraTentativasBarradasPorBloqueio() {
        when(repository.buscarInstantesDeFalha(any(), anyList(), any(), any())).thenReturn(List.of());

        service.avaliarPosFalha(UUID.randomUUID(), "u@sep.test");

        ArgumentCaptor<List<LoginAttemptStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(repository).buscarInstantesDeFalha(eq("u@sep.test"), statuses.capture(), any(), any());
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(LoginAttemptStatus.SENHA_INVALIDA, LoginAttemptStatus.TOTP_INVALIDO);
    }
}
