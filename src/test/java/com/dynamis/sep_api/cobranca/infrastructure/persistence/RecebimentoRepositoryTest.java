package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class RecebimentoRepositoryTest {

    @Autowired
    private AgendaPagamentoRepository agendaRepository;

    @Autowired
    private ParcelaCobrancaRepository parcelaRepository;

    @Autowired
    private RecebimentoRepository recebimentoRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private PropostaCreditoRepository propostaRepository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private ParcelaCobranca parcela;
    private UUID operadorId;

    @BeforeEach
    void setup() {
        agendaRepository.deleteAll();
        contratoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario tomador = usuarioRepository.saveAndFlush(Usuario.criar("rec-tomador@sep.test", "hash", Role.CLIENTE));
        Usuario op = usuarioRepository.saveAndFlush(Usuario.criar("rec-op@sep.test", "hash", Role.FINANCEIRO));
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                tomador.getId(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1)));
        PropostaCredito p = propostaRepository.saveAndFlush(
                PropostaCredito.criar(tomador.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12));
        Contrato contrato =
                contratoRepository.saveAndFlush(Contrato.criar(p.getId(), tomador.getId(), TipoContrato.MUTUO));
        AgendaPagamento agenda = agendaRepository.saveAndFlush(AgendaPagamento.criar(
                contrato.getId(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 6, 1)))));
        parcela = agenda.getParcelas().get(0);
        operadorId = op.getId();
    }

    private static final BigDecimal DEVIDO = new BigDecimal("100.00");

    @Test
    void findByIdempotencyKey_retornaExistente() {
        Recebimento r = parcela.registrarRecebimento(
                new BigDecimal("40.00"),
                DEVIDO,
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                "comp-1",
                "key-A",
                null,
                operadorId);
        parcelaRepository.saveAndFlush(parcela);

        Recebimento achado = recebimentoRepository.findByIdempotencyKey("key-A").orElseThrow();

        assertThat(achado.getId()).isEqualTo(r.getId());
        assertThat(recebimentoRepository.findByIdempotencyKey("nao-existe")).isEmpty();
    }

    @Test
    void uniqueIdempotencyKey_segundoFalha() {
        parcela.registrarRecebimento(
                new BigDecimal("10.00"),
                DEVIDO,
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-dup",
                null,
                operadorId);
        parcelaRepository.saveAndFlush(parcela);

        ParcelaCobranca outraParcela = criarOutraParcelaIsolada();
        outraParcela.registrarRecebimento(
                new BigDecimal("10.00"),
                new BigDecimal("50.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-dup",
                null,
                operadorId);

        assertThatThrownBy(() -> parcelaRepository.saveAndFlush(outraParcela))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ParcelaCobranca criarOutraParcelaIsolada() {
        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("outro@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                u.getId(), new Cpf("11144477735"), "Outro", LocalDate.of(1990, 1, 1)));
        PropostaCredito p2 = propostaRepository.saveAndFlush(
                PropostaCredito.criar(u.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("5000"), 6));
        Contrato c2 = contratoRepository.saveAndFlush(Contrato.criar(p2.getId(), u.getId(), TipoContrato.MUTUO));
        AgendaPagamento outraAgenda = agendaRepository.saveAndFlush(AgendaPagamento.criar(
                c2.getId(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("50.00")), LocalDate.of(2026, 7, 1)))));
        return outraAgenda.getParcelas().get(0);
    }
}
