package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class SolicitacaoOnboardingRepositoryTest {

    private static final String CPF_VALIDO = "52998224725";

    @Autowired
    private SolicitacaoOnboardingRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID usuarioId;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("kyc-pf@sep.test", "hash", Role.CLIENTE));
        usuarioId = u.getId();
    }

    @Test
    void persistirERecuperarSolicitacaoPreservaCampos() {
        SolicitacaoOnboarding nova = SolicitacaoOnboarding.criar(
                usuarioId, new Cpf(CPF_VALIDO), "Joao da Silva", LocalDate.of(1990, 1, 1));

        SolicitacaoOnboarding salva = repository.saveAndFlush(nova);
        SolicitacaoOnboarding recarregada = repository.findById(salva.getId()).orElseThrow();

        assertThat(recarregada.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(recarregada.getCpf()).isEqualTo(CPF_VALIDO);
        assertThat(recarregada.getStatus()).isEqualTo(StatusOnboarding.INICIADO);
        assertThat(recarregada.getRevisaoDocumentos()).isZero();
        assertThat(recarregada.getCriadoPor()).isEqualTo(AuditorAwareImpl.SYSTEM);
    }

    @Test
    void existsByCpfAndStatusInIdentificaSolicitacaoAtiva() {
        repository.saveAndFlush(SolicitacaoOnboarding.criar(
                usuarioId, new Cpf(CPF_VALIDO), "Joao", LocalDate.of(1990, 1, 1)));

        boolean ativo =
                repository.existsByCpfAndStatusIn(CPF_VALIDO, List.of(StatusOnboarding.INICIADO));
        boolean reprovado =
                repository.existsByCpfAndStatusIn(CPF_VALIDO, List.of(StatusOnboarding.REPROVADO));

        assertThat(ativo).isTrue();
        assertThat(reprovado).isFalse();
    }

    @Test
    void findByIdAndUsuarioIdRetornaSomenteParaOwner() {
        SolicitacaoOnboarding salva = repository.saveAndFlush(SolicitacaoOnboarding.criar(
                usuarioId, new Cpf(CPF_VALIDO), "Joao", LocalDate.of(1990, 1, 1)));

        assertThat(repository.findByIdAndUsuarioId(salva.getId(), usuarioId)).isPresent();
        assertThat(repository.findByIdAndUsuarioId(salva.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByIdVerificacaoExternaLocalizaPosDispatch() {
        SolicitacaoOnboarding s = SolicitacaoOnboarding.criar(
                usuarioId, new Cpf(CPF_VALIDO), "Joao", LocalDate.of(1990, 1, 1));
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("ext-celcoin-001");
        repository.saveAndFlush(s);

        assertThat(repository.findByIdVerificacaoExterna("ext-celcoin-001")).isPresent();
        assertThat(repository.findByIdVerificacaoExterna("inexistente")).isEmpty();
    }
}
