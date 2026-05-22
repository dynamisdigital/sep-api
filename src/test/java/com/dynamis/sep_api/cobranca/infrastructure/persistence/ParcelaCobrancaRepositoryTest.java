package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
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
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class ParcelaCobrancaRepositoryTest {

    @Autowired
    private AgendaPagamentoRepository agendaRepository;

    @Autowired
    private ParcelaCobrancaRepository parcelaRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private PropostaCreditoRepository propostaRepository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID contratoId;
    private AgendaPagamento agendaSalva;

    @BeforeEach
    void setup() {
        agendaRepository.deleteAll();
        contratoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("parc-repo@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                u.getId(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1)));
        PropostaCredito p = propostaRepository.saveAndFlush(
                PropostaCredito.criar(u.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12));
        Contrato contrato = contratoRepository.saveAndFlush(Contrato.criar(p.getId(), u.getId(), TipoContrato.MUTUO));
        contratoId = contrato.getId();

        List<ParcelaPlanejada> planejadas = List.of(
                new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 1, 1)),
                new ParcelaPlanejada(
                        2, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 6, 1)),
                new ParcelaPlanejada(
                        3, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 12, 1)));
        agendaSalva = agendaRepository.saveAndFlush(AgendaPagamento.criar(contratoId, planejadas));
    }

    @Test
    void findByStatusAndDataVencimentoBefore_filtraPendentesAntigas() {
        List<ParcelaCobranca> vencidas =
                parcelaRepository.findByStatusAndDataVencimentoBefore(StatusParcela.PENDENTE, LocalDate.of(2026, 7, 1));

        assertThat(vencidas).hasSize(2);
        assertThat(vencidas).extracting(ParcelaCobranca::getNumero).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void findByIdForUpdate_retornaParcela() {
        ParcelaCobranca primeira = agendaSalva.getParcelas().get(0);

        ParcelaCobranca lock =
                parcelaRepository.findByIdForUpdate(primeira.getId()).orElseThrow();

        assertThat(lock.getId()).isEqualTo(primeira.getId());
    }

    @Test
    void findByAgenda_ContratoIdOrderByNumeroAsc() {
        List<ParcelaCobranca> ordenadas = parcelaRepository.findByAgenda_ContratoIdOrderByNumeroAsc(contratoId);

        assertThat(ordenadas).hasSize(3);
        assertThat(ordenadas).extracting(ParcelaCobranca::getNumero).containsExactly(1, 2, 3);
    }
}
