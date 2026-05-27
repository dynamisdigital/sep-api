package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class AgendaPagamentoRepositoryTest {

    @Autowired
    private AgendaPagamentoRepository agendaRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private PropostaCreditoRepository propostaRepository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID contratoId;

    @BeforeEach
    void setup() {
        agendaRepository.deleteAll();
        contratoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("cob-repo@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                u.getId(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1)));
        PropostaCredito p = propostaRepository.saveAndFlush(
                PropostaCredito.criar(u.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12));
        Contrato contrato = contratoRepository.saveAndFlush(Contrato.criar(p.getId(), u.getId(), TipoContrato.MUTUO));
        contratoId = contrato.getId();
    }

    @Test
    void salvarEBuscarPorContrato() {
        AgendaPagamento agenda = AgendaPagamento.criar(contratoId, planejadasDe(2));

        AgendaPagamento salva = agendaRepository.saveAndFlush(agenda);
        AgendaPagamento achada =
                agendaRepository.findByContratoIdAndAtivaTrue(contratoId).orElseThrow();

        assertThat(achada.getId()).isEqualTo(salva.getId());
        assertThat(achada.getNumeroParcelas()).isEqualTo(2);
        assertThat(achada.getParcelas()).hasSize(2);
        assertThat(agendaRepository.existsByContratoIdAndAtivaTrue(contratoId)).isTrue();
    }

    @Test
    void uniqueContrato_segundaFalha() {
        agendaRepository.saveAndFlush(AgendaPagamento.criar(contratoId, planejadasDe(2)));

        AgendaPagamento duplicada = AgendaPagamento.criar(contratoId, planejadasDe(2));

        assertThatThrownBy(() -> agendaRepository.saveAndFlush(duplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByContratoId_inexistenteRetornaEmpty() {
        assertThat(agendaRepository.findByContratoIdAndAtivaTrue(UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void agendaSubstituta_apenasUmaAtivaPorContrato() {
        // Sprint 13 Task 13.6 fix review manual: UNIQUE parcial WHERE ativa=true permite
        // multiplas agendas no historico desde que apenas uma esteja ativa por contrato.
        AgendaPagamento original = agendaRepository.saveAndFlush(AgendaPagamento.criar(contratoId, planejadasDe(2)));
        original.marcarSubstituida();
        agendaRepository.saveAndFlush(original);

        AgendaPagamento substituta = AgendaPagamento.criarSubstituta(contratoId, original.getId(), planejadasDe(3));
        AgendaPagamento salva = agendaRepository.saveAndFlush(substituta);

        assertThat(salva.getAgendaSubstituidaId()).isEqualTo(original.getId());
        assertThat(salva.isAtiva()).isTrue();
        AgendaPagamento ativa =
                agendaRepository.findByContratoIdAndAtivaTrue(contratoId).orElseThrow();
        assertThat(ativa.getId()).isEqualTo(salva.getId());
        // Historico preservado: total 2 agendas no banco.
        assertThat(agendaRepository.count()).isEqualTo(2);
    }

    @Test
    void agendaSubstituta_semInativarAnterior_falhaPorUniqueParcial() {
        agendaRepository.saveAndFlush(AgendaPagamento.criar(contratoId, planejadasDe(2)));
        AgendaPagamento substitutaSemInativar =
                AgendaPagamento.criarSubstituta(contratoId, UUID.randomUUID(), planejadasDe(2));

        assertThatThrownBy(() -> agendaRepository.saveAndFlush(substitutaSemInativar))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static List<ParcelaPlanejada> planejadasDe(int qtd) {
        return java.util.stream.IntStream.rangeClosed(1, qtd)
                .mapToObj(i -> new ParcelaPlanejada(
                        i,
                        ComposicaoValor.principalApenas(new BigDecimal("100.00")),
                        LocalDate.of(2026, 6, 1).plusMonths(i - 1L)))
                .toList();
    }
}
