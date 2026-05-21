package com.dynamis.sep_api.contratos.infrastructure.persistence;

import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class ContratoRepositoryTest {

    private static final String HASH = "0".repeat(64);

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private PropostaCreditoRepository propostaRepository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID tomadorId;
    private UUID propostaId;

    @BeforeEach
    void setup() {
        contratoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("contrato-repo@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                u.getId(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1)));
        PropostaCredito p = propostaRepository.saveAndFlush(
                PropostaCredito.criar(u.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12));
        tomadorId = u.getId();
        propostaId = p.getId();
    }

    @Test
    void persistirEBuscarPorProposta() {
        Contrato salvo = contratoRepository.saveAndFlush(Contrato.criar(propostaId, tomadorId, TipoContrato.MUTUO));

        Contrato achado = contratoRepository.findByPropostaId(propostaId).orElseThrow();

        assertThat(achado.getId()).isEqualTo(salvo.getId());
        assertThat(achado.getStatus()).isEqualTo(StatusFormalizacao.GERADO);
        assertThat(contratoRepository.existsByPropostaId(propostaId)).isTrue();
    }

    @Test
    void uniqueProposta_segundoContratoFalha() {
        contratoRepository.saveAndFlush(Contrato.criar(propostaId, tomadorId, TipoContrato.MUTUO));
        Contrato duplicado = Contrato.criar(propostaId, tomadorId, TipoContrato.MUTUO);

        assertThatThrownBy(() -> contratoRepository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cascadeSalvaVersoesEClausulas() {
        Contrato contrato = Contrato.criar(propostaId, tomadorId, TipoContrato.MUTUO);
        contrato.adicionarVersao("conteudo v1", HASH);
        contrato.getVersoes().get(0).adicionarClausula(1, "Foro", "Sao Paulo");
        contrato.getVersoes().get(0).adicionarClausula(2, "Multa", "10%");

        Contrato salvo = contratoRepository.saveAndFlush(contrato);
        UUID contratoId = salvo.getId();

        Contrato recarregado = contratoRepository.findById(contratoId).orElseThrow();
        assertThat(recarregado.getVersoes()).hasSize(1);
        assertThat(recarregado.getVersoes().get(0).getClausulas()).hasSize(2);
        assertThat(recarregado.getVersoes().get(0).getClausulas().get(0).getTitulo())
                .isEqualTo("Foro");
        assertThat(recarregado.getStatus()).isEqualTo(StatusFormalizacao.AGUARDANDO_ACEITE);
    }

    @Test
    void findByIdForUpdate_retornaContrato() {
        Contrato salvo = contratoRepository.saveAndFlush(Contrato.criar(propostaId, tomadorId, TipoContrato.MUTUO));

        Contrato lock = contratoRepository.findByIdForUpdate(salvo.getId()).orElseThrow();

        assertThat(lock.getId()).isEqualTo(salvo.getId());
    }

    @Test
    void findByPropostaId_inexistenteRetornaEmpty() {
        assertThat(contratoRepository.findByPropostaId(UUID.randomUUID())).isEmpty();
    }
}
