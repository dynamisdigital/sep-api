package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.dto.IniciarConsentimentoOpenFinanceCommand;
import com.dynamis.sep_api.credito.application.port.out.OpenFinanceProvider;
import com.dynamis.sep_api.credito.application.port.out.dto.RequisicaoConsentimento;
import com.dynamis.sep_api.credito.application.port.out.dto.RespostaConsentimento;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceConsentimentoIniciadoEvent;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoAtivoException;
import com.dynamis.sep_api.credito.domain.exception.OpenFinanceFluxoInvalidoException;
import com.dynamis.sep_api.credito.domain.exception.OwnershipPropostaException;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.ConsentimentoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IniciarConsentimentoOpenFinanceUseCaseTest {

    private PropostaCreditoRepository propostaRepository;
    private ConsentimentoOpenFinanceRepository consentimentoRepository;
    private OpenFinanceProvider provider;
    private ApplicationEventPublisher eventPublisher;
    private IniciarConsentimentoOpenFinanceUseCase useCase;

    @BeforeEach
    void setup() {
        propostaRepository = mock(PropostaCreditoRepository.class);
        consentimentoRepository = mock(ConsentimentoOpenFinanceRepository.class);
        provider = mock(OpenFinanceProvider.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new IniciarConsentimentoOpenFinanceUseCase(
                propostaRepository, consentimentoRepository, provider, eventPublisher);
        when(consentimentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(consentimentoRepository.findFirstByPropostaIdAndStatusOrderByDataInicioDesc(
                        any(), eq(StatusConsentimento.PENDENTE)))
                .thenReturn(Optional.empty());
    }

    private PropostaCredito propostaEmAnalise(UUID tomadorId) {
        return PropostaCredito.criar(tomadorId, UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12);
    }

    private void mockProviderOk() {
        when(provider.iniciarConsentimento(any(RequisicaoConsentimento.class), anyString()))
                .thenReturn(new RespostaConsentimento(
                        "ext-celcoin-1",
                        "https://celcoin/auth/1",
                        OffsetDateTime.now().plusDays(30)));
    }

    @Test
    void iniciaConsentimentoQuandoPropostaEmAnalise() {
        UUID tomador = UUID.randomUUID();
        PropostaCredito p = propostaEmAnalise(tomador);
        when(propostaRepository.findById(p.getId())).thenReturn(Optional.of(p));
        mockProviderOk();

        ConsentimentoOpenFinance c = useCase.executar(
                new IniciarConsentimentoOpenFinanceCommand(p.getId(), tomador, "52998224725", "https://sep/cb"));

        assertThat(c.getStatus()).isEqualTo(StatusConsentimento.PENDENTE);
        assertThat(c.getIdExternoCelcoin()).isEqualTo("ext-celcoin-1");
        verify(eventPublisher).publishEvent(any(OpenFinanceConsentimentoIniciadoEvent.class));
    }

    @Test
    void rejeita404QuandoPropostaNaoExiste() {
        UUID id = UUID.randomUUID();
        when(propostaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(new IniciarConsentimentoOpenFinanceCommand(
                        id, UUID.randomUUID(), "52998224725", "https://sep/cb")))
                .isInstanceOf(PropostaNaoEncontradaException.class);
    }

    @Test
    void rejeita403QuandoTomadorNaoDono() {
        UUID dono = UUID.randomUUID();
        PropostaCredito p = propostaEmAnalise(dono);
        when(propostaRepository.findById(p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> useCase.executar(new IniciarConsentimentoOpenFinanceCommand(
                        p.getId(), UUID.randomUUID(), "52998224725", "https://sep/cb")))
                .isInstanceOf(OwnershipPropostaException.class);
    }

    @Test
    void rejeita422QuandoPropostaAprovada() {
        UUID tomador = UUID.randomUUID();
        PropostaCredito p = propostaEmAnalise(tomador);
        p.aplicarSugestaoMotor(com.dynamis.sep_api.credito.domain.vo.StatusProposta.PRE_APROVADA);
        p.registrarDecisaoManual(com.dynamis.sep_api.credito.domain.vo.DecisaoParecer.APROVAR);
        when(propostaRepository.findById(p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> useCase.executar(new IniciarConsentimentoOpenFinanceCommand(
                        p.getId(), tomador, "52998224725", "https://sep/cb")))
                .isInstanceOf(OpenFinanceFluxoInvalidoException.class);
    }

    @Test
    void rejeita409QuandoJaExisteConsentimentoPendente() {
        UUID tomador = UUID.randomUUID();
        PropostaCredito p = propostaEmAnalise(tomador);
        when(propostaRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(consentimentoRepository.findFirstByPropostaIdAndStatusOrderByDataInicioDesc(
                        p.getId(), StatusConsentimento.PENDENTE))
                .thenReturn(Optional.of(ConsentimentoOpenFinance.iniciar(
                        p.getId(), tomador, "u", "ext-old", OffsetDateTime.now().plusDays(30))));

        assertThatThrownBy(() -> useCase.executar(new IniciarConsentimentoOpenFinanceCommand(
                        p.getId(), tomador, "52998224725", "https://sep/cb")))
                .isInstanceOf(ConsentimentoAtivoException.class);
    }
}
