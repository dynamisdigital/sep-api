package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.domain.model.LoginAttempt;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
import com.dynamis.sep_api.identity.infrastructure.persistence.LoginAttemptRepository;
import com.dynamis.sep_api.shared.audit.AuditLogSeguranca;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegistrarTentativaLoginUseCaseTest {

    private LoginAttemptRepository attemptRepository;
    private AuditLogSegurancaRepository auditRepository;
    private RegistrarTentativaLoginUseCase useCase;

    @BeforeEach
    void setup() {
        attemptRepository = mock(LoginAttemptRepository.class);
        auditRepository = mock(AuditLogSegurancaRepository.class);
        useCase = new RegistrarTentativaLoginUseCase(attemptRepository, auditRepository, new ObjectMapper());
    }

    /**
     * O {@code username} vem do corpo da request e a coluna e {@code jsonb}. A concatenacao antiga
     * produzia JSON invalido para um username com aspas, que o Postgres rejeita — perdendo o rastro
     * inteiro. O {@code @Email} do DTO nao barra: a RFC admite local-part entre aspas.
     */
    @Test
    void usernameComAspasEBarrasProduzJsonValidoQueSobreviveAoRoundTrip() throws Exception {
        String hostil = "\"a\\b\"@sep.test";

        useCase.registrar(UUID.randomUUID(), hostil, "127.0.0.1", "ua", LoginAttemptStatus.SENHA_INVALIDA);

        ArgumentCaptor<AuditLogSeguranca> captor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(auditRepository).save(captor.capture());
        JsonNode json = new ObjectMapper().readTree(captor.getValue().getDetalhes());
        assertThat(json.get("username").asText()).isEqualTo(hostil);
        assertThat(json.get("status").asText()).isEqualTo("SENHA_INVALIDA");
    }

    @Test
    void sucessoPersisteAttemptEAuditLoginOk() {
        UUID usuarioId = UUID.randomUUID();

        useCase.registrar(usuarioId, "u@sep.test", "127.0.0.1", "ua", LoginAttemptStatus.SUCESSO);

        ArgumentCaptor<LoginAttempt> attemptCaptor = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getStatus()).isEqualTo(LoginAttemptStatus.SUCESSO);

        ArgumentCaptor<AuditLogSeguranca> auditCaptor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getTipo()).isEqualTo(TipoEventoSeguranca.LOGIN_OK);
    }

    @Test
    void totpInvalidoMapeiaParaTotpFail() {
        useCase.registrar(UUID.randomUUID(), "u@sep.test", "127.0.0.1", "ua", LoginAttemptStatus.TOTP_INVALIDO);

        ArgumentCaptor<AuditLogSeguranca> captor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoEventoSeguranca.TOTP_FAIL);
    }

    /**
     * A tentativa barrada tem tipo proprio (Sprint 34 Task 34.1). Reusar {@code LOCKOUT} misturaria
     * dois fatos distintos na mesma trilha — "bloqueou agora", que sai uma vez por bloqueio, e
     * "tentou durante o bloqueio", que sai a cada tentativa — e obrigaria a parsear {@code jsonb}
     * para separa-los.
     */
    @Test
    void contaBloqueadaMapeiaParaTipoProprioENaoParaLockout() {
        useCase.registrar(UUID.randomUUID(), "u@sep.test", "127.0.0.1", "ua", LoginAttemptStatus.CONTA_BLOQUEADA);

        ArgumentCaptor<AuditLogSeguranca> captor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoEventoSeguranca.LOCKOUT_TENTATIVA_BARRADA);
    }

    @Test
    void senhaInvalidaMapeiaParaLoginFail() {
        useCase.registrar(null, "u@sep.test", "127.0.0.1", "ua", LoginAttemptStatus.SENHA_INVALIDA);

        ArgumentCaptor<AuditLogSeguranca> captor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoEventoSeguranca.LOGIN_FAIL);
    }
}
