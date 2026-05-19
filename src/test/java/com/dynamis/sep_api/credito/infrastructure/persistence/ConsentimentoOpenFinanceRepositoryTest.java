package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class ConsentimentoOpenFinanceRepositoryTest {

    @Autowired
    private ConsentimentoOpenFinanceRepository consentimentoRepository;

    @Autowired
    private PropostaCreditoRepository propostaRepository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID propostaId;
    private UUID tomadorId;

    @BeforeEach
    void setup() {
        consentimentoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("of-repo@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                u.getId(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1)));
        PropostaCredito p = propostaRepository.saveAndFlush(
                PropostaCredito.criar(u.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12));
        propostaId = p.getId();
        tomadorId = u.getId();
    }

    @Test
    void persistirEBuscarPorIdExterno() {
        ConsentimentoOpenFinance c = consentimentoRepository.saveAndFlush(ConsentimentoOpenFinance.iniciar(
                propostaId,
                tomadorId,
                "https://celcoin/auth/abc",
                "ext-celcoin-1",
                OffsetDateTime.now().plusDays(30)));

        ConsentimentoOpenFinance r =
                consentimentoRepository.findByIdExternoCelcoin("ext-celcoin-1").orElseThrow();
        assertThat(r.getId()).isEqualTo(c.getId());
        assertThat(r.getStatus()).isEqualTo(StatusConsentimento.PENDENTE);
    }

    @Test
    void buscarMaisRecentePorProposta() {
        ConsentimentoOpenFinance c1 = consentimentoRepository.saveAndFlush(ConsentimentoOpenFinance.iniciar(
                propostaId, tomadorId, "u1", "ext-1", OffsetDateTime.now().plusDays(30)));
        c1.negar();
        consentimentoRepository.saveAndFlush(c1);
        ConsentimentoOpenFinance c2 = consentimentoRepository.saveAndFlush(ConsentimentoOpenFinance.iniciar(
                propostaId, tomadorId, "u2", "ext-2", OffsetDateTime.now().plusDays(30)));

        ConsentimentoOpenFinance maisRecente = consentimentoRepository
                .findFirstByPropostaIdOrderByDataInicioDesc(propostaId)
                .orElseThrow();
        assertThat(maisRecente.getId()).isEqualTo(c2.getId());
    }

    @Test
    void uniqueParcialBloqueia2PendentesNaMesmaProposta() {
        consentimentoRepository.saveAndFlush(ConsentimentoOpenFinance.iniciar(
                propostaId, tomadorId, "u1", "ext-1", OffsetDateTime.now().plusDays(30)));
        ConsentimentoOpenFinance duplicado = ConsentimentoOpenFinance.iniciar(
                propostaId, tomadorId, "u2", "ext-2", OffsetDateTime.now().plusDays(30));

        assertThatThrownBy(() -> consentimentoRepository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueParcialPermiteNovoPendenteAposNegado() {
        ConsentimentoOpenFinance c1 = consentimentoRepository.saveAndFlush(ConsentimentoOpenFinance.iniciar(
                propostaId, tomadorId, "u1", "ext-1", OffsetDateTime.now().plusDays(30)));
        c1.negar();
        consentimentoRepository.saveAndFlush(c1);

        ConsentimentoOpenFinance c2 = consentimentoRepository.saveAndFlush(ConsentimentoOpenFinance.iniciar(
                propostaId, tomadorId, "u2", "ext-2", OffsetDateTime.now().plusDays(30)));

        assertThat(c2.getStatus()).isEqualTo(StatusConsentimento.PENDENTE);
    }
}
