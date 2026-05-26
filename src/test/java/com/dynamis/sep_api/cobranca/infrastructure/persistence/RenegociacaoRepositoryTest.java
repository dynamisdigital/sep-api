package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class RenegociacaoRepositoryTest {

    @Autowired
    private AgendaPagamentoRepository agendaRepository;

    @Autowired
    private RenegociacaoRepository renegociacaoRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private PropostaCreditoRepository propostaRepository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private ParcelaCobranca parcela;
    private AgendaPagamento agenda;
    private UUID tomadorId;

    @BeforeEach
    void setup() {
        renegociacaoRepository.deleteAll();
        agendaRepository.deleteAll();
        contratoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("ren-repo@sep.test", "hash", Role.CLIENTE));
        tomadorId = u.getId();
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                u.getId(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1)));
        PropostaCredito p = propostaRepository.saveAndFlush(
                PropostaCredito.criar(u.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12));
        Contrato contrato = contratoRepository.saveAndFlush(Contrato.criar(p.getId(), u.getId(), TipoContrato.MUTUO));

        agenda = agendaRepository.saveAndFlush(AgendaPagamento.criar(
                contrato.getId(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 6, 1)))));
        parcela = agenda.getParcelas().get(0);
    }

    @Test
    void persisteEEncontraPorParcelaEStatus() {
        Renegociacao r = renegociacaoRepository.saveAndFlush(novaProposta());

        assertThat(renegociacaoRepository.findByParcelaOriginalIdAndStatus(
                        parcela.getId(), StatusRenegociacao.PROPOSTA))
                .map(Renegociacao::getId)
                .contains(r.getId());
    }

    @Test
    void uniqueParcial_naoPermiteDuasPropostasAtivasMesmaParcela() {
        renegociacaoRepository.saveAndFlush(novaProposta());

        Renegociacao duplicada = novaProposta();

        assertThatThrownBy(() -> renegociacaoRepository.saveAndFlush(duplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueParcial_aceita_naoBloqueia_novaPropostaFutura() {
        Renegociacao primeira = renegociacaoRepository.saveAndFlush(novaProposta());
        primeira.recusar(OffsetDateTime.now());
        renegociacaoRepository.saveAndFlush(primeira);

        Renegociacao segunda = renegociacaoRepository.saveAndFlush(novaProposta());

        assertThat(segunda.getId()).isNotEqualTo(primeira.getId());
    }

    @Test
    void findByStatusEDataExpiracao_filtraPropostasVencidas() {
        Renegociacao expirada = renegociacaoRepository.saveAndFlush(novaProposta());
        OffsetDateTime depoisDaExpiracao = expirada.getDataExpiracao().plusSeconds(1);

        List<Renegociacao> vencidas = renegociacaoRepository.findByStatusAndDataExpiracaoBefore(
                StatusRenegociacao.PROPOSTA, depoisDaExpiracao);

        assertThat(vencidas).extracting(Renegociacao::getId).contains(expirada.getId());
    }

    private Renegociacao novaProposta() {
        OffsetDateTime agora = OffsetDateTime.parse("2026-06-10T09:00:00-03:00");
        return Renegociacao.propor(
                parcela.getId(),
                agenda.getId(),
                tomadorId,
                StatusParcela.ATRASADA,
                new BigDecimal("110.00"),
                LocalDate.of(2026, 7, 10),
                3,
                BigDecimal.ZERO,
                "Renegociacao por dificuldade temporaria",
                UUID.randomUUID(),
                agora,
                agora.plusDays(7));
    }
}
