package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.model.ScoreInterno;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
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
class ScoreInternoRepositoryTest {

    @Autowired
    private ScoreInternoRepository scoreRepository;

    @Autowired
    private PropostaCreditoRepository propostaRepository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID propostaId;

    @BeforeEach
    void setup() {
        scoreRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("score-repo@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                u.getId(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1)));
        PropostaCredito p = propostaRepository.saveAndFlush(
                PropostaCredito.criar(u.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12));
        propostaId = p.getId();
    }

    @Test
    void persistirEBuscarPorProposta() {
        ScoreInterno s = scoreRepository.saveAndFlush(
                ScoreInterno.calculado(propostaId, 850, StatusProposta.PRE_APROVADA, 1, 2));

        ScoreInterno r = scoreRepository.findByPropostaId(propostaId).orElseThrow();
        assertThat(r.getId()).isEqualTo(s.getId());
        assertThat(r.getValor()).isEqualTo(850);
        assertThat(r.getStatusSugerido()).isEqualTo(StatusProposta.PRE_APROVADA);
    }

    @Test
    void findByPropostaIdRetornaVazioParaPropostaSemScore() {
        assertThat(scoreRepository.findByPropostaId(UUID.randomUUID())).isEmpty();
    }
}
