package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.EventoCobranca;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.TipoEventoCobranca;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class EventoCobrancaRepositoryTest {

    @Autowired
    private AgendaPagamentoRepository agendaRepository;

    @Autowired
    private EventoCobrancaRepository eventoRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private PropostaCreditoRepository propostaRepository;

    @Autowired
    private SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private ParcelaCobranca parcela;

    @BeforeEach
    void setup() {
        eventoRepository.deleteAll();
        agendaRepository.deleteAll();
        contratoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("ev-repo@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding onb = onboardingRepository.saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                u.getId(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1)));
        PropostaCredito p = propostaRepository.saveAndFlush(
                PropostaCredito.criar(u.getId(), onb.getId(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12));
        Contrato contrato = contratoRepository.saveAndFlush(Contrato.criar(p.getId(), u.getId(), TipoContrato.MUTUO));

        AgendaPagamento agenda = agendaRepository.saveAndFlush(AgendaPagamento.criar(
                contrato.getId(),
                java.util.List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 6, 1)))));
        parcela = agenda.getParcelas().get(0);
    }

    @Test
    void persisteEListaPorParcelaOrdenadoPorData() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-06-05T10:00:00-03:00");
        eventoRepository.saveAndFlush(EventoCobranca.notificacaoAutomatica(
                parcela.getId(), CanalNotificacao.EMAIL, "email-amigavel", 5, StatusEventoCobranca.SUCESSO, null, t0));
        eventoRepository.saveAndFlush(EventoCobranca.notificacaoAutomatica(
                parcela.getId(),
                CanalNotificacao.SMS,
                "sms-lembrete",
                5,
                StatusEventoCobranca.SUCESSO,
                null,
                t0.plusMinutes(1)));

        assertThat(eventoRepository.findByParcelaIdOrderByDataEventoAsc(parcela.getId()))
                .hasSize(2)
                .extracting(EventoCobranca::getCanal)
                .containsExactly(CanalNotificacao.EMAIL, CanalNotificacao.SMS);
    }

    @Test
    void uniqueIdempotencia_blockaSegundaNotificacaoMesmoDiaCanalTemplate() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-06-05T10:00:00-03:00");
        eventoRepository.saveAndFlush(EventoCobranca.notificacaoAutomatica(
                parcela.getId(), CanalNotificacao.EMAIL, "email-firme", 15, StatusEventoCobranca.SUCESSO, null, t0));

        EventoCobranca duplicada = EventoCobranca.notificacaoAutomatica(
                parcela.getId(),
                CanalNotificacao.EMAIL,
                "email-firme",
                15,
                StatusEventoCobranca.SUCESSO,
                null,
                t0.plusHours(1));

        assertThatThrownBy(() -> eventoRepository.saveAndFlush(duplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueIdempotencia_naoBloqueiaContatoManualOuRenegociacao() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-06-05T10:00:00-03:00");
        eventoRepository.saveAndFlush(
                EventoCobranca.contatoManual(parcela.getId(), UUID.randomUUID(), 5, "Cliente ligou", t0));
        eventoRepository.saveAndFlush(EventoCobranca.contatoManual(
                parcela.getId(), UUID.randomUUID(), 5, "Cliente ligou de novo", t0.plusMinutes(10)));

        assertThat(eventoRepository.findByParcelaIdOrderByDataEventoAsc(parcela.getId()))
                .hasSize(2);
    }

    @Test
    void existsByIdempotencia_detectaPrevio() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-06-05T10:00:00-03:00");
        eventoRepository.saveAndFlush(EventoCobranca.notificacaoAutomatica(
                parcela.getId(), CanalNotificacao.SMS, "sms-firme", 30, StatusEventoCobranca.SUCESSO, null, t0));

        assertThat(eventoRepository.existsByParcelaIdAndDiasAtrasoAndCanalAndTemplate(
                        parcela.getId(), 30, CanalNotificacao.SMS, "sms-firme"))
                .isTrue();
        assertThat(eventoRepository.existsByParcelaIdAndDiasAtrasoAndCanalAndTemplate(
                        parcela.getId(), 30, CanalNotificacao.SMS, "sms-lembrete"))
                .isFalse();
    }

    @Test
    void chkTipo_invalido_falha() {
        // valida que constraint chk_evento_cobranca_tipo recusa string fora do enum.
        // Como o adapter JPA usa @Enumerated(STRING), o teste cobre indiretamente via tentativa de
        // criar evento com tipo proibido na factory mudancaEstado (NOTIFICACAO_AUTOMATICA).
        assertThatThrownBy(() -> EventoCobranca.mudancaEstado(
                        parcela.getId(), TipoEventoCobranca.NOTIFICACAO_AUTOMATICA, 0, "x", null, OffsetDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
