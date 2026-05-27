package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.ResultadoVerificacao;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class ResultadoVerificacaoRepositoryTest {

    @Autowired
    private ResultadoVerificacaoRepository repository;

    @Autowired
    private SolicitacaoOnboardingRepository solicitacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID solicitacaoId;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        solicitacaoRepository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("kyc-r@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding s = solicitacaoRepository.saveAndFlush(
                SolicitacaoOnboarding.criarPessoa(u.getId(), new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1)));
        solicitacaoId = s.getId();
    }

    @Test
    void findBySolicitacaoIdRetornaResultadoQuandoExiste() {
        repository.saveAndFlush(
                ResultadoVerificacao.registrar(solicitacaoId, StatusOnboarding.APROVADO, null, "{\"ok\":true}"));

        assertThat(repository.findBySolicitacaoId(solicitacaoId)).isPresent();
        assertThat(repository.findBySolicitacaoId(UUID.randomUUID())).isEmpty();
    }
}
