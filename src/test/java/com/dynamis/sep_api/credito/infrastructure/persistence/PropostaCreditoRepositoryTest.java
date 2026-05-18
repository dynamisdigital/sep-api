package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class PropostaCreditoRepositoryTest {

    @Autowired
    private PropostaCreditoRepository repository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID tomadorId;
    private UUID onboardingId;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario tomador = usuarioRepository.saveAndFlush(Usuario.criar("credito-repo@sep.test", "hash", Role.CLIENTE));
        tomadorId = tomador.getId();

        SolicitacaoOnboarding onb = SolicitacaoOnboarding.criarPessoa(
                tomadorId, new Cpf("52998224725"), "Joao da Silva", LocalDate.of(1990, 1, 1));
        onboardingId = onboardingRepository.saveAndFlush(onb).getId();
    }

    @Test
    void persistirERecuperarProposta() {
        PropostaCredito p = repository.saveAndFlush(novaProposta());

        PropostaCredito r = repository.findById(p.getId()).orElseThrow();
        assertThat(r.getTomadorId()).isEqualTo(tomadorId);
        assertThat(r.getSolicitacaoOnboardingId()).isEqualTo(onboardingId);
        assertThat(r.getStatus()).isEqualTo(StatusProposta.EM_ANALISE);
        assertThat(r.getTipoOperacao()).isEqualTo(TipoOperacao.CAPITAL_GIRO);
        assertThat(r.getMoeda()).isEqualTo("BRL");
        assertThat(r.getCriadoPor()).isEqualTo(AuditorAwareImpl.SYSTEM);
    }

    @Test
    void findByIdAndTomadorIdRestringePorOwner() {
        PropostaCredito p = repository.saveAndFlush(novaProposta());

        assertThat(repository.findByIdAndTomadorId(p.getId(), tomadorId)).isPresent();
        assertThat(repository.findByIdAndTomadorId(p.getId(), UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void findByStatusListaPorEstado() {
        repository.saveAndFlush(novaProposta());
        Page<PropostaCredito> page = repository.findByStatus(StatusProposta.EM_ANALISE, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(StatusProposta.EM_ANALISE);
    }

    @Test
    void findByStatusInListaPendentes() {
        repository.saveAndFlush(novaProposta());

        Page<PropostaCredito> pendentes = repository.findByStatusIn(
                List.of(StatusProposta.EM_ANALISE, StatusProposta.PRE_APROVADA, StatusProposta.PENDENCIA),
                PageRequest.of(0, 10));
        assertThat(pendentes.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findByTomadorIdListaApenasDoTomador() {
        repository.saveAndFlush(novaProposta());

        Usuario outro = usuarioRepository.saveAndFlush(Usuario.criar("outro-tomador@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding outroOnb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                outro.getId(), new Cpf("11144477735"), "Outro", LocalDate.of(1985, 1, 1)));
        repository.saveAndFlush(
                PropostaCredito.criar(outro.getId(), outroOnb.getId(), TipoOperacao.OUTROS, Money.brl("5000"), 6));

        Page<PropostaCredito> page = repository.findByTomadorId(tomadorId, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTomadorId()).isEqualTo(tomadorId);
    }

    private PropostaCredito novaProposta() {
        return PropostaCredito.criar(tomadorId, onboardingId, TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12);
    }
}
