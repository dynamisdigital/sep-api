package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.AssociarOperacaoFinanciadaCommand;
import com.dynamis.sep_api.credores.application.port.out.ConsultarContratoParaCarteiraCredoraPort;
import com.dynamis.sep_api.credores.application.port.out.ConsultarPropostasElegiveisParaCredoraPort;
import com.dynamis.sep_api.credores.application.port.out.ContratoCarteiraView;
import com.dynamis.sep_api.credores.application.port.out.PropostaElegivelView;
import com.dynamis.sep_api.credores.application.service.OperacaoCarteiraEnricher;
import com.dynamis.sep_api.credores.domain.event.InteresseCredoraCanceladoEvent;
import com.dynamis.sep_api.credores.domain.event.InteresseCredoraRegistradoEvent;
import com.dynamis.sep_api.credores.domain.event.OperacaoFinanciadaAssociadaEvent;
import com.dynamis.sep_api.credores.domain.exception.ContratoNaoElegivelException;
import com.dynamis.sep_api.credores.domain.exception.CredoraNaoElegivelException;
import com.dynamis.sep_api.credores.domain.exception.InteresseDuplicadoException;
import com.dynamis.sep_api.credores.domain.exception.InteresseNaoEncontradoException;
import com.dynamis.sep_api.credores.domain.exception.OperacaoFinanciadaDuplicadaException;
import com.dynamis.sep_api.credores.domain.exception.OportunidadeIndisponivelException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.InteresseCredora;
import com.dynamis.sep_api.credores.domain.model.OportunidadeInvestimento;
import com.dynamis.sep_api.credores.domain.vo.StatusInteresseCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.InteresseCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests com mocks dos use cases de carteira credora (Sprint 17 Task 17.4). */
@ExtendWith(MockitoExtension.class)
class CarteiraCredoraUseCaseTest {

    @Mock
    EmpresaCredoraRepository empresaRepository;

    @Mock
    OportunidadeInvestimentoRepository oportunidadeRepository;

    @Mock
    InteresseCredoraRepository interesseRepository;

    @Mock
    OperacaoFinanciadaRepository operacaoRepository;

    @Mock
    ConsultarContratoParaCarteiraCredoraPort contratoPort;

    @Mock
    ConsultarPropostasElegiveisParaCredoraPort propostasPort;

    @Mock
    OperacaoCarteiraEnricher enricher;

    @Mock
    ApplicationEventPublisher eventPublisher;

    private static final UUID USUARIO = UUID.randomUUID();

    private EmpresaCredora credoraElegivel() {
        EmpresaCredora c = EmpresaCredora.cadastrar(USUARIO, UUID.randomUUID(), "11222333000181", "Credora LTDA");
        c.registrarElegivel(); // ATIVA + ELEGIVEL
        return c;
    }

    private EmpresaCredora credoraPendente() {
        return EmpresaCredora.cadastrar(USUARIO, UUID.randomUUID(), "11222333000181", "Credora LTDA");
    }

    // ===== RegistrarInteresse =====

    @Test
    void registrarInteresseSucesso() {
        var uc = new RegistrarInteresseCredoraUseCase(
                empresaRepository, oportunidadeRepository, interesseRepository, eventPublisher);
        UUID oportunidadeId = UUID.randomUUID();
        EmpresaCredora credora = credoraElegivel();
        OportunidadeInvestimento oportunidade =
                OportunidadeInvestimento.criar(UUID.randomUUID(), null, new BigDecimal("1000.00"), 6, null);

        when(empresaRepository.findByUsuarioId(USUARIO)).thenReturn(Optional.of(credora));
        when(oportunidadeRepository.findById(oportunidadeId)).thenReturn(Optional.of(oportunidade));
        when(interesseRepository.existsByEmpresaCredoraIdAndOportunidadeIdAndStatus(
                        credora.getId(), oportunidadeId, StatusInteresseCredora.ATIVO))
                .thenReturn(false);
        when(interesseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var view = uc.executar(USUARIO, oportunidadeId);

        assertThat(view.oportunidadeId()).isEqualTo(oportunidadeId);
        assertThat(view.status()).isEqualTo(StatusInteresseCredora.ATIVO);
        verify(eventPublisher).publishEvent(any(InteresseCredoraRegistradoEvent.class));
    }

    @Test
    void registrarInteresseCredoraInelegivel() {
        var uc = new RegistrarInteresseCredoraUseCase(
                empresaRepository, oportunidadeRepository, interesseRepository, eventPublisher);
        when(empresaRepository.findByUsuarioId(USUARIO)).thenReturn(Optional.of(credoraPendente()));

        assertThatThrownBy(() -> uc.executar(USUARIO, UUID.randomUUID()))
                .isInstanceOf(CredoraNaoElegivelException.class);
        verify(interesseRepository, never()).save(any());
    }

    @Test
    void registrarInteresseOportunidadeIndisponivel() {
        var uc = new RegistrarInteresseCredoraUseCase(
                empresaRepository, oportunidadeRepository, interesseRepository, eventPublisher);
        UUID oportunidadeId = UUID.randomUUID();
        OportunidadeInvestimento oportunidade =
                OportunidadeInvestimento.criar(UUID.randomUUID(), null, new BigDecimal("1000.00"), 6, null);
        oportunidade.encerrar();

        when(empresaRepository.findByUsuarioId(USUARIO)).thenReturn(Optional.of(credoraElegivel()));
        when(oportunidadeRepository.findById(oportunidadeId)).thenReturn(Optional.of(oportunidade));

        assertThatThrownBy(() -> uc.executar(USUARIO, oportunidadeId))
                .isInstanceOf(OportunidadeIndisponivelException.class);
    }

    @Test
    void registrarInteresseDuplicado() {
        var uc = new RegistrarInteresseCredoraUseCase(
                empresaRepository, oportunidadeRepository, interesseRepository, eventPublisher);
        UUID oportunidadeId = UUID.randomUUID();
        EmpresaCredora credora = credoraElegivel();
        OportunidadeInvestimento oportunidade =
                OportunidadeInvestimento.criar(UUID.randomUUID(), null, new BigDecimal("1000.00"), 6, null);

        when(empresaRepository.findByUsuarioId(USUARIO)).thenReturn(Optional.of(credora));
        when(oportunidadeRepository.findById(oportunidadeId)).thenReturn(Optional.of(oportunidade));
        when(interesseRepository.existsByEmpresaCredoraIdAndOportunidadeIdAndStatus(
                        credora.getId(), oportunidadeId, StatusInteresseCredora.ATIVO))
                .thenReturn(true);

        assertThatThrownBy(() -> uc.executar(USUARIO, oportunidadeId)).isInstanceOf(InteresseDuplicadoException.class);
        verify(interesseRepository, never()).save(any());
    }

    // ===== CancelarInteresse =====

    @Test
    void cancelarInteresseSucesso() {
        var uc = new CancelarInteresseCredoraUseCase(empresaRepository, interesseRepository, eventPublisher);
        UUID oportunidadeId = UUID.randomUUID();
        EmpresaCredora credora = credoraElegivel();
        InteresseCredora interesse = InteresseCredora.registrar(credora.getId(), oportunidadeId);

        when(empresaRepository.findByUsuarioId(USUARIO)).thenReturn(Optional.of(credora));
        when(interesseRepository.findByEmpresaCredoraIdAndOportunidadeIdAndStatus(
                        credora.getId(), oportunidadeId, StatusInteresseCredora.ATIVO))
                .thenReturn(Optional.of(interesse));
        when(interesseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        uc.executar(USUARIO, oportunidadeId);

        assertThat(interesse.getStatus()).isEqualTo(StatusInteresseCredora.CANCELADO);
        verify(eventPublisher).publishEvent(any(InteresseCredoraCanceladoEvent.class));
    }

    @Test
    void cancelarInteresseInexistente() {
        var uc = new CancelarInteresseCredoraUseCase(empresaRepository, interesseRepository, eventPublisher);
        UUID oportunidadeId = UUID.randomUUID();
        EmpresaCredora credora = credoraElegivel();
        when(empresaRepository.findByUsuarioId(USUARIO)).thenReturn(Optional.of(credora));
        when(interesseRepository.findByEmpresaCredoraIdAndOportunidadeIdAndStatus(
                        credora.getId(), oportunidadeId, StatusInteresseCredora.ATIVO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> uc.executar(USUARIO, oportunidadeId))
                .isInstanceOf(InteresseNaoEncontradoException.class);
    }

    // ===== AssociarOperacaoFinanciada =====

    @Test
    void associarOperacaoSucesso() {
        var uc = new AssociarOperacaoFinanciadaUseCase(
                empresaRepository, oportunidadeRepository, operacaoRepository, contratoPort, enricher, eventPublisher);
        EmpresaCredora credora = credoraElegivel();
        UUID contratoId = UUID.randomUUID();
        var cmd = new AssociarOperacaoFinanciadaCommand(
                credora.getId(), contratoId, null, "Associacao assistida", UUID.randomUUID());

        when(empresaRepository.findById(credora.getId())).thenReturn(Optional.of(credora));
        when(contratoPort.consultarPorId(contratoId))
                .thenReturn(Optional.of(new ContratoCarteiraView(contratoId, UUID.randomUUID(), "ASSINADO")));
        when(operacaoRepository.existsByEmpresaCredoraIdAndContratoId(credora.getId(), contratoId))
                .thenReturn(false);
        when(operacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        uc.executar(cmd);

        verify(operacaoRepository).save(any());
        verify(eventPublisher).publishEvent(any(OperacaoFinanciadaAssociadaEvent.class));
    }

    @Test
    void associarOperacaoContratoNaoElegivel() {
        var uc = new AssociarOperacaoFinanciadaUseCase(
                empresaRepository, oportunidadeRepository, operacaoRepository, contratoPort, enricher, eventPublisher);
        EmpresaCredora credora = credoraElegivel();
        UUID contratoId = UUID.randomUUID();
        var cmd = new AssociarOperacaoFinanciadaCommand(credora.getId(), contratoId, null, "x", UUID.randomUUID());

        when(empresaRepository.findById(credora.getId())).thenReturn(Optional.of(credora));
        when(contratoPort.consultarPorId(contratoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uc.executar(cmd)).isInstanceOf(ContratoNaoElegivelException.class);
        verify(operacaoRepository, never()).save(any());
    }

    @Test
    void associarOperacaoContratoNaoAssinado() {
        var uc = new AssociarOperacaoFinanciadaUseCase(
                empresaRepository, oportunidadeRepository, operacaoRepository, contratoPort, enricher, eventPublisher);
        EmpresaCredora credora = credoraElegivel();
        UUID contratoId = UUID.randomUUID();
        var cmd = new AssociarOperacaoFinanciadaCommand(credora.getId(), contratoId, null, "x", UUID.randomUUID());

        when(empresaRepository.findById(credora.getId())).thenReturn(Optional.of(credora));
        when(contratoPort.consultarPorId(contratoId))
                .thenReturn(Optional.of(new ContratoCarteiraView(contratoId, UUID.randomUUID(), "GERADO")));

        assertThatThrownBy(() -> uc.executar(cmd)).isInstanceOf(ContratoNaoElegivelException.class);
        verify(operacaoRepository, never()).save(any());
    }

    @Test
    void associarOperacaoDuplicada() {
        var uc = new AssociarOperacaoFinanciadaUseCase(
                empresaRepository, oportunidadeRepository, operacaoRepository, contratoPort, enricher, eventPublisher);
        EmpresaCredora credora = credoraElegivel();
        UUID contratoId = UUID.randomUUID();
        var cmd = new AssociarOperacaoFinanciadaCommand(credora.getId(), contratoId, null, "x", UUID.randomUUID());

        when(empresaRepository.findById(credora.getId())).thenReturn(Optional.of(credora));
        when(contratoPort.consultarPorId(contratoId))
                .thenReturn(Optional.of(new ContratoCarteiraView(contratoId, UUID.randomUUID(), "ASSINADO")));
        when(operacaoRepository.existsByEmpresaCredoraIdAndContratoId(credora.getId(), contratoId))
                .thenReturn(true);

        assertThatThrownBy(() -> uc.executar(cmd)).isInstanceOf(OperacaoFinanciadaDuplicadaException.class);
        verify(operacaoRepository, never()).save(any());
    }

    @Test
    void associarOperacaoContratoDivergenteDaOportunidade() {
        var uc = new AssociarOperacaoFinanciadaUseCase(
                empresaRepository, oportunidadeRepository, operacaoRepository, contratoPort, enricher, eventPublisher);
        EmpresaCredora credora = credoraElegivel();
        UUID oportunidadeId = UUID.randomUUID();
        UUID contratoDaOportunidade = UUID.randomUUID();
        UUID contratoDivergente = UUID.randomUUID();
        OportunidadeInvestimento oportunidade = OportunidadeInvestimento.criar(
                UUID.randomUUID(), contratoDaOportunidade, new BigDecimal("1000.00"), 6, null);
        var cmd = new AssociarOperacaoFinanciadaCommand(
                credora.getId(), contratoDivergente, oportunidadeId, "x", UUID.randomUUID());

        when(empresaRepository.findById(credora.getId())).thenReturn(Optional.of(credora));
        when(oportunidadeRepository.findById(oportunidadeId)).thenReturn(Optional.of(oportunidade));

        assertThatThrownBy(() -> uc.executar(cmd))
                .isInstanceOf(com.dynamis.sep_api.shared.exception.ValidacaoException.class);
        verify(operacaoRepository, never()).save(any());
    }

    // ===== Sincronizar =====

    @Test
    void sincronizarCriaEAtualizaOportunidades() {
        var uc = new SincronizarOportunidadesInvestimentoUseCase(propostasPort, oportunidadeRepository);
        UUID propostaNova = UUID.randomUUID();
        UUID propostaExistente = UUID.randomUUID();
        OportunidadeInvestimento existente =
                OportunidadeInvestimento.criar(propostaExistente, null, new BigDecimal("100.00"), 3, null);

        when(propostasPort.listarElegiveis())
                .thenReturn(List.of(
                        new PropostaElegivelView(propostaNova, null, new BigDecimal("500.00"), 6, null),
                        new PropostaElegivelView(
                                propostaExistente, UUID.randomUUID(), new BigDecimal("999.00"), 9, null)));
        when(oportunidadeRepository.findByPropostaId(propostaNova)).thenReturn(Optional.empty());
        when(oportunidadeRepository.findByPropostaId(propostaExistente)).thenReturn(Optional.of(existente));
        when(oportunidadeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int total = uc.executar();

        assertThat(total).isEqualTo(2);
        assertThat(existente.getValor()).isEqualByComparingTo("999.00"); // atualizado pelo snapshot
    }
}
