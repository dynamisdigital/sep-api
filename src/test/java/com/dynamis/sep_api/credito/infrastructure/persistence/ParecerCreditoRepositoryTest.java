package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.ParecerCredito;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class ParecerCreditoRepositoryTest {

    @Autowired
    private ParecerCreditoRepository parecerRepository;

    @Autowired
    private PropostaCreditoRepository propostaRepository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID propostaId;
    private UUID pareceristaId;

    @BeforeEach
    void setup() {
        parecerRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario tomador =
                usuarioRepository.saveAndFlush(Usuario.criar("tomador-parecer@sep.test", "hash", Role.CLIENTE));
        Usuario parecerista =
                usuarioRepository.saveAndFlush(Usuario.criar("financeiro-parecer@sep.test", "hash", Role.ADMIN));
        pareceristaId = parecerista.getId();
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                tomador.getId(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1)));
        propostaId = propostaRepository
                .saveAndFlush(PropostaCredito.criar(
                        tomador.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12))
                .getId();
    }

    @Test
    void persistirERetornarPorVersao() {
        ParecerCredito p1 = parecerRepository.saveAndFlush(ParecerCredito.registrar(
                propostaId, pareceristaId, DecisaoParecer.PENDENCIA, "Aguardando documento adicional", 700, 1));
        ParecerCredito p2 = parecerRepository.saveAndFlush(ParecerCredito.registrar(
                propostaId, pareceristaId, DecisaoParecer.APROVAR, "Documento recebido, aprovado", 850, 2));

        List<ParecerCredito> ordenados = parecerRepository.findByPropostaIdOrderByVersaoAsc(propostaId);
        assertThat(ordenados).extracting(ParecerCredito::getId).containsExactly(p1.getId(), p2.getId());

        ParecerCredito ultimo = parecerRepository
                .findTopByPropostaIdOrderByVersaoDesc(propostaId)
                .orElseThrow();
        assertThat(ultimo.getVersao()).isEqualTo(2);
        assertThat(ultimo.getDecisao()).isEqualTo(DecisaoParecer.APROVAR);
    }

    @Test
    void countByPropostaIdRetornaTotal() {
        parecerRepository.saveAndFlush(ParecerCredito.registrar(
                propostaId, pareceristaId, DecisaoParecer.PENDENCIA, "Justificativa de pendencia", 600, 1));
        assertThat(parecerRepository.countByPropostaId(propostaId)).isEqualTo(1);
    }
}
