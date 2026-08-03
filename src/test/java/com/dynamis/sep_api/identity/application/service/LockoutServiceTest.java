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

import java.time.Duration;
import java.time.OffsetDateTime;
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

    private LoginAttemptRepository repository;
    private AuditLogSegurancaRepository auditRepository;
    private EmailService emailService;
    private LockoutProperties properties;
    private LockoutService service;

    @BeforeEach
    void setup() {
        repository = mock(LoginAttemptRepository.class);
        auditRepository = mock(AuditLogSegurancaRepository.class);
        emailService = mock(EmailService.class);
        properties = new LockoutProperties();
        service = new LockoutService(repository, auditRepository, properties, emailService, new ObjectMapper());
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
    private static List<OffsetDateTime> falhasRecentes(int quantidade, Duration intervalo) {
        OffsetDateTime agora = OffsetDateTime.now();
        return IntStream.range(0, quantidade)
                .mapToObj(i -> agora.minus(intervalo.multipliedBy(i)))
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

        OffsetDateTime antes = OffsetDateTime.now();
        service.verificar("u@sep.test");
        OffsetDateTime depois = OffsetDateTime.now();

        ArgumentCaptor<OffsetDateTime> inicio = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).buscarInstantesDeFalha(eq("u@sep.test"), anyList(), inicio.capture(), any());
        // Sem relogio injetavel, a unica asserção exata e o intervalo em que `now()` pode ter caido.
        assertThat(inicio.getValue()).isBetween(antes.minus(janelaEsperada), depois.minus(janelaEsperada));
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
