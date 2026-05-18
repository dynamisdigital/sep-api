package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarPropostaUseCaseTest {

    @Test
    void retornaPropostaQuandoExiste() {
        PropostaCreditoRepository repo = mock(PropostaCreditoRepository.class);
        ConsultarPropostaUseCase uc = new ConsultarPropostaUseCase(repo);
        PropostaCredito p = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.OUTROS, new Money(new BigDecimal("1000"), "BRL"), 6);
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));

        assertThat(uc.executar(p.getId())).isSameAs(p);
    }

    @Test
    void lancaPropostaNaoEncontrada() {
        PropostaCreditoRepository repo = mock(PropostaCreditoRepository.class);
        ConsultarPropostaUseCase uc = new ConsultarPropostaUseCase(repo);
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uc.executar(id)).isInstanceOf(PropostaNaoEncontradaException.class);
    }

    @Test
    void ownershipPositivo() {
        PropostaCreditoRepository repo = mock(PropostaCreditoRepository.class);
        ConsultarPropostaUseCase uc = new ConsultarPropostaUseCase(repo);
        UUID id = UUID.randomUUID();
        UUID dono = UUID.randomUUID();
        PropostaCredito p = PropostaCredito.criar(
                dono, UUID.randomUUID(), TipoOperacao.OUTROS, new Money(new BigDecimal("1000"), "BRL"), 6);
        when(repo.findByIdAndTomadorId(id, dono)).thenReturn(Optional.of(p));

        assertThat(uc.executarComOwnership(id, dono)).isSameAs(p);
    }

    @Test
    void ownershipNegativoLancaNaoEncontrada() {
        PropostaCreditoRepository repo = mock(PropostaCreditoRepository.class);
        ConsultarPropostaUseCase uc = new ConsultarPropostaUseCase(repo);
        UUID id = UUID.randomUUID();
        UUID outro = UUID.randomUUID();
        when(repo.findByIdAndTomadorId(id, outro)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uc.executarComOwnership(id, outro)).isInstanceOf(PropostaNaoEncontradaException.class);
    }
}
