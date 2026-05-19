package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.port.out.OpenFinanceProvider;
import com.dynamis.sep_api.credito.application.port.out.dto.MovimentacaoConsolidada;
import com.dynamis.sep_api.credito.application.service.OpenFinancePayloadSanitizer;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceDadosRecebidosEvent;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoNaoAutorizadoException;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoNaoEncontradoException;
import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance;
import com.dynamis.sep_api.credito.infrastructure.persistence.ConsentimentoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.MovimentacaoOpenFinanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultarMovimentacaoOpenFinanceUseCaseTest {

    private ConsentimentoOpenFinanceRepository consentimentoRepository;
    private MovimentacaoOpenFinanceRepository movimentacaoRepository;
    private OpenFinanceProvider provider;
    private OpenFinancePayloadSanitizer sanitizer;
    private ApplicationEventPublisher publisher;
    private ConsultarMovimentacaoOpenFinanceUseCase useCase;

    @BeforeEach
    void setup() {
        consentimentoRepository = mock(ConsentimentoOpenFinanceRepository.class);
        movimentacaoRepository = mock(MovimentacaoOpenFinanceRepository.class);
        provider = mock(OpenFinanceProvider.class);
        sanitizer = new OpenFinancePayloadSanitizer(new ObjectMapper());
        publisher = mock(ApplicationEventPublisher.class);
        useCase = new ConsultarMovimentacaoOpenFinanceUseCase(
                consentimentoRepository, movimentacaoRepository, provider, sanitizer, publisher);
        when(movimentacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ConsentimentoOpenFinance autorizado() {
        ConsentimentoOpenFinance c = ConsentimentoOpenFinance.iniciar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "u",
                "ext-1",
                OffsetDateTime.now().plusDays(30));
        c.autorizar();
        return c;
    }

    @Test
    void consultaProviderEPersisteSnapshot() {
        ConsentimentoOpenFinance c = autorizado();
        when(consentimentoRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(movimentacaoRepository.findByConsentimentoId(c.getId())).thenReturn(Optional.empty());
        when(provider.consultarMovimentacao(eq("ext-1"), anyString()))
                .thenReturn(new MovimentacaoConsolidada(
                        "{\"saldo\":3000}",
                        new BigDecimal("10000.00"),
                        new BigDecimal("7000.00"),
                        new BigDecimal("3000.00"),
                        6));

        MovimentacaoOpenFinance snap = useCase.executar(c.getId());

        assertThat(snap.getNumeroMesesAvaliados()).isEqualTo(6);
        assertThat(snap.getMediaEntradasMensal()).isEqualByComparingTo("10000.00");
        verify(publisher).publishEvent(any(OpenFinanceDadosRecebidosEvent.class));
    }

    @Test
    void rejeita404QuandoConsentimentoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(consentimentoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(id)).isInstanceOf(ConsentimentoNaoEncontradoException.class);
    }

    @Test
    void rejeita422QuandoConsentimentoPendente() {
        ConsentimentoOpenFinance c = ConsentimentoOpenFinance.iniciar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "u",
                "ext-1",
                OffsetDateTime.now().plusDays(30));
        when(consentimentoRepository.findById(c.getId())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> useCase.executar(c.getId())).isInstanceOf(ConsentimentoNaoAutorizadoException.class);
    }

    @Test
    void idempotenteQuandoSnapshotJaExiste() {
        ConsentimentoOpenFinance c = autorizado();
        MovimentacaoOpenFinance existente = MovimentacaoOpenFinance.registrar(
                c.getId(),
                c.getPropostaId(),
                "{}",
                new BigDecimal("5000"),
                new BigDecimal("3000"),
                new BigDecimal("2000"),
                3);
        when(consentimentoRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(movimentacaoRepository.findByConsentimentoId(c.getId())).thenReturn(Optional.of(existente));

        MovimentacaoOpenFinance r = useCase.executar(c.getId());

        assertThat(r).isSameAs(existente);
        verify(provider, never()).consultarMovimentacao(anyString(), anyString());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void payloadSanitizadoRemoveCamposSensiveis() {
        ConsentimentoOpenFinance c = autorizado();
        when(consentimentoRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(movimentacaoRepository.findByConsentimentoId(c.getId())).thenReturn(Optional.empty());
        when(provider.consultarMovimentacao(anyString(), anyString()))
                .thenReturn(new MovimentacaoConsolidada(
                        "{\"saldo\":3000,\"cpf\":\"52998224725\",\"account_number\":\"12345\",\"transactions\":[{\"v\":1}]}",
                        new BigDecimal("10000.00"),
                        new BigDecimal("7000.00"),
                        new BigDecimal("3000.00"),
                        6));

        MovimentacaoOpenFinance snap = useCase.executar(c.getId());

        assertThat(snap.getPayloadConsolidado())
                .doesNotContain("cpf")
                .doesNotContain("account_number")
                .doesNotContain("transactions")
                .contains("saldo");
    }
}
