package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.application.exception.ContaBloqueadaException;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
import com.dynamis.sep_api.identity.infrastructure.persistence.LoginAttemptRepository;
import com.dynamis.sep_api.identity.infrastructure.security.LockoutProperties;
import com.dynamis.sep_api.shared.audit.AuditLogSeguranca;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.shared.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.OffsetDateTime;
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
        service = new LockoutService(repository, auditRepository, properties, emailService);
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

        service.verificar("u@sep.test");

        ArgumentCaptor<OffsetDateTime> inicio = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).buscarInstantesDeFalha(eq("u@sep.test"), anyList(), inicio.capture(), any());
        long minutosLidos =
                Duration.between(inicio.getValue(), OffsetDateTime.now()).toMinutes();
        assertThat(minutosLidos).isEqualTo(properties.getWindowMinutes() + properties.getLockoutMinutes());
    }

    @Test
    void avaliarPosFalhaEmiteEmailEAuditQuandoAtingeLimite() {
        UUID usuarioId = UUID.randomUUID();
        when(repository.countByUsernameAndStatusInAndJanela(eq("u@sep.test"), anyList(), any()))
                .thenReturn((long) properties.getMaxAttempts());

        service.avaliarPosFalha(usuarioId, "u@sep.test");

        ArgumentCaptor<AuditLogSeguranca> captor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoEventoSeguranca.LOCKOUT);
        verify(emailService).enviar(eq("u@sep.test"), any(), any());
    }

    @Test
    void avaliarPosFalhaNaoEmiteQuandoAbaixoDoLimite() {
        when(repository.countByUsernameAndStatusInAndJanela(any(), anyList(), any()))
                .thenReturn((long) properties.getMaxAttempts() - 1);

        service.avaliarPosFalha(UUID.randomUUID(), "u@sep.test");

        verify(auditRepository, never()).save(any());
        verify(emailService, never()).enviar(any(), any(), any());
    }

    @Test
    void avaliarPosFalhaUsaJanelaCurta() {
        UUID usuarioId = UUID.randomUUID();
        when(repository.countByUsernameAndStatusInAndJanela(any(), anyList(), any()))
                .thenReturn((long) properties.getMaxAttempts());

        service.avaliarPosFalha(usuarioId, "u@sep.test");

        ArgumentCaptor<OffsetDateTime> ts = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<List<LoginAttemptStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(repository).countByUsernameAndStatusInAndJanela(eq("u@sep.test"), statuses.capture(), ts.capture());
        assertThat(statuses.getValue()).contains(LoginAttemptStatus.SENHA_INVALIDA, LoginAttemptStatus.TOTP_INVALIDO);
    }
}
