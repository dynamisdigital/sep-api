package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class MovimentacaoOpenFinanceRepositoryTest {

    @Autowired
    private MovimentacaoOpenFinanceRepository movimentacaoRepository;

    @Autowired
    private ConsentimentoOpenFinanceRepository consentimentoRepository;

    @Autowired
    private PropostaCreditoRepository propostaRepository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID propostaId;
    private UUID consentimentoId;

    @BeforeEach
    void setup() {
        movimentacaoRepository.deleteAll();
        consentimentoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("mov-of-repo@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                u.getId(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1)));
        PropostaCredito p = propostaRepository.saveAndFlush(
                PropostaCredito.criar(u.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12));
        propostaId = p.getId();
        ConsentimentoOpenFinance c = consentimentoRepository.saveAndFlush(ConsentimentoOpenFinance.iniciar(
                propostaId, u.getId(), "u", "ext-1", OffsetDateTime.now().plusDays(30)));
        consentimentoId = c.getId();
    }

    @Test
    void persistirSnapshotEbuscarMaisRecente() {
        MovimentacaoOpenFinance m = movimentacaoRepository.saveAndFlush(MovimentacaoOpenFinance.registrar(
                consentimentoId,
                propostaId,
                "{\"meses\":6}",
                new BigDecimal("10000.00"),
                new BigDecimal("7000.00"),
                new BigDecimal("3000.00"),
                6));

        MovimentacaoOpenFinance r = movimentacaoRepository
                .findFirstByPropostaIdOrderByDataRecebimentoDesc(propostaId)
                .orElseThrow();
        assertThat(r.getId()).isEqualTo(m.getId());
        assertThat(r.getNumeroMesesAvaliados()).isEqualTo(6);
        assertThat(r.getMediaEntradasMensal()).isEqualByComparingTo("10000.00");
    }

    @Test
    void buscarPorConsentimentoRetornaVazioQuandoAusente() {
        assertThat(movimentacaoRepository.findFirstByConsentimentoIdOrderByDataRecebimentoDesc(UUID.randomUUID()))
                .isEmpty();
    }
}
