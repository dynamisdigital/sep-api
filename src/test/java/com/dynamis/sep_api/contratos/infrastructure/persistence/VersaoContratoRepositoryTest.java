package com.dynamis.sep_api.contratos.infrastructure.persistence;

import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.VersaoContrato;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class VersaoContratoRepositoryTest {

    private static final String HASH_1 = "1".repeat(64);
    private static final String HASH_2 = "2".repeat(64);
    private static final String HASH_3 = "3".repeat(64);

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private VersaoContratoRepository versaoRepository;

    @Autowired
    private PropostaCreditoRepository propostaRepository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EntityManager entityManager;

    private Contrato contrato;

    @BeforeEach
    void setup() {
        contratoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("versao-repo@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                u.getId(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1)));
        PropostaCredito p = propostaRepository.saveAndFlush(
                PropostaCredito.criar(u.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12));
        contrato = contratoRepository.saveAndFlush(Contrato.criar(p.getId(), u.getId(), TipoContrato.MUTUO));
    }

    @Test
    void findByContratoIdOrdenado_retornaEmOrdemAscendente() {
        contrato.adicionarVersao("v1", HASH_1);
        contrato.adicionarVersao("v2", HASH_2);
        contrato.adicionarVersao("v3", HASH_3);
        contratoRepository.saveAndFlush(contrato);

        List<VersaoContrato> versoes = versaoRepository.findByContratoIdOrdenado(contrato.getId());

        assertThat(versoes).hasSize(3);
        assertThat(versoes).extracting(VersaoContrato::getNumero).containsExactly(1, 2, 3);
    }

    @Test
    void findVigente_retornaVersaoComMaiorNumero() {
        contrato.adicionarVersao("v1", HASH_1);
        contrato.adicionarVersao("v2", HASH_2);
        contratoRepository.saveAndFlush(contrato);

        VersaoContrato vigente = versaoRepository.findVigente(contrato.getId()).orElseThrow();

        assertThat(vigente.getNumero()).isEqualTo(2);
        assertThat(vigente.getConteudoTexto()).isEqualTo("v2");
    }

    @Test
    void findVigente_semVersoesRetornaEmpty() {
        assertThat(versaoRepository.findVigente(contrato.getId())).isEmpty();
    }

    @Test
    void uniqueContratoNumero_segundaVersaoComMesmoNumeroFalha() {
        VersaoContrato v1 = VersaoContrato.criar(contrato, 1, "v1", HASH_1);
        versaoRepository.saveAndFlush(v1);
        VersaoContrato duplicada = VersaoContrato.criar(contrato, 1, "outro", HASH_2);

        // unique (contrato_id, numero) viola; pode emergir como DataIntegrityViolation ou PersistenceException
        assertThatThrownBy(() -> {
                    versaoRepository.saveAndFlush(duplicada);
                    entityManager.flush();
                })
                .isInstanceOfAny(
                        org.springframework.dao.DataIntegrityViolationException.class, PersistenceException.class);
    }
}
