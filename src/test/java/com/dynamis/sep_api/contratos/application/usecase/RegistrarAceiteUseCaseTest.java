package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.usecase.command.RegistrarAceiteCommand;
import com.dynamis.sep_api.contratos.domain.event.ContratoAceitoEvent;
import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.exception.ContratoNaoEncontradoException;
import com.dynamis.sep_api.contratos.domain.exception.ContratoOwnershipException;
import com.dynamis.sep_api.contratos.domain.model.AceiteContrato;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.AceiteContratoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.shared.exception.ConflitoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarAceiteUseCaseTest {

    private static final String HASH = "1".repeat(64);

    @Mock
    private ContratoRepository contratoRepository;

    @Mock
    private AceiteContratoRepository aceiteRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RegistrarAceiteUseCase useCase;

    private Contrato contrato;
    private UUID tomadorId;

    @BeforeEach
    void setup() {
        tomadorId = UUID.randomUUID();
        contrato = Contrato.criar(UUID.randomUUID(), tomadorId, TipoContrato.MUTUO);
        contrato.adicionarVersao("conteudo v1", HASH);
    }

    @Test
    void executar_sucessoTransicionaParaAceitoEPublicaEvento() {
        when(contratoRepository.findByIdForUpdate(contrato.getId())).thenReturn(Optional.of(contrato));
        when(aceiteRepository.existsByVersaoId(any())).thenReturn(false);
        when(contratoRepository.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aceiteRepository.save(any(AceiteContrato.class))).thenAnswer(inv -> inv.getArgument(0));

        Contrato resultado = useCase.executar(
                new RegistrarAceiteCommand(contrato.getId(), tomadorId, "203.0.113.42", "Mozilla/5.0"));

        assertThat(resultado.getStatus()).isEqualTo(StatusFormalizacao.ACEITO);
        verify(aceiteRepository, times(1)).save(any(AceiteContrato.class));
        verify(contratoRepository, times(1)).save(contrato);
        verify(eventPublisher, times(1))
                .publishEvent(argThat((Object e) -> e instanceof ContratoAceitoEvent evento
                        && evento.contratoId().equals(contrato.getId())
                        && evento.tomadorId().equals(tomadorId)
                        && "203.0.113.42".equals(evento.ipOrigem())
                        && "Mozilla/5.0".equals(evento.userAgentOrigem())
                        && evento.hashSha256().equals(HASH)));
    }

    @Test
    void executar_contratoInexistente_lanca404() {
        UUID id = UUID.randomUUID();
        when(contratoRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(new RegistrarAceiteCommand(id, tomadorId, "ip", "ua")))
                .isInstanceOf(ContratoNaoEncontradoException.class);
    }

    @Test
    void executar_tomadorAlheio_lanca403() {
        UUID outroTomador = UUID.randomUUID();
        when(contratoRepository.findByIdForUpdate(contrato.getId())).thenReturn(Optional.of(contrato));

        assertThatThrownBy(
                        () -> useCase.executar(new RegistrarAceiteCommand(contrato.getId(), outroTomador, "ip", "ua")))
                .isInstanceOf(ContratoOwnershipException.class);
        verify(aceiteRepository, never()).save(any());
    }

    @Test
    void executar_contratoJaAceito_lanca409() {
        contrato.marcarAceito();
        when(contratoRepository.findByIdForUpdate(contrato.getId())).thenReturn(Optional.of(contrato));

        assertThatThrownBy(() -> useCase.executar(new RegistrarAceiteCommand(contrato.getId(), tomadorId, "ip", "ua")))
                .isInstanceOf(ContratoEstadoInvalidoException.class);
    }

    @Test
    void executar_contratoCancelado_lanca409() {
        contrato.cancelar();
        when(contratoRepository.findByIdForUpdate(contrato.getId())).thenReturn(Optional.of(contrato));

        assertThatThrownBy(() -> useCase.executar(new RegistrarAceiteCommand(contrato.getId(), tomadorId, "ip", "ua")))
                .isInstanceOf(ContratoEstadoInvalidoException.class);
    }

    @Test
    void executar_versaoVigenteJaAceita_lancaConflito() {
        when(contratoRepository.findByIdForUpdate(contrato.getId())).thenReturn(Optional.of(contrato));
        when(aceiteRepository.existsByVersaoId(any())).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(new RegistrarAceiteCommand(contrato.getId(), tomadorId, "ip", "ua")))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("ja foi aceita");
        verify(aceiteRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_raceUniqueVersao_traduzParaConflito() {
        // Cenario: existsByVersaoId retorna false (caller 1 e 2 passam pela checagem),
        // mas save do caller 2 viola UNIQUE versao_id porque caller 1 ja persistiu.
        when(contratoRepository.findByIdForUpdate(contrato.getId())).thenReturn(Optional.of(contrato));
        when(aceiteRepository.existsByVersaoId(any())).thenReturn(false);
        when(aceiteRepository.save(any(AceiteContrato.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "unique violation aceite_contrato_versao_id"));

        assertThatThrownBy(() -> useCase.executar(new RegistrarAceiteCommand(contrato.getId(), tomadorId, "ip", "ua")))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("ja foi aceita");
        // contrato NAO deve ter sido transicionado nem persistido
        assertThat(contrato.getStatus()).isEqualTo(StatusFormalizacao.AGUARDANDO_ACEITE);
        verify(contratoRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_aceitaIpEUserAgentNull() {
        when(contratoRepository.findByIdForUpdate(contrato.getId())).thenReturn(Optional.of(contrato));
        when(aceiteRepository.existsByVersaoId(any())).thenReturn(false);
        when(contratoRepository.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aceiteRepository.save(any(AceiteContrato.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.executar(new RegistrarAceiteCommand(contrato.getId(), tomadorId, null, null));

        verify(eventPublisher)
                .publishEvent(argThat((Object e) -> e instanceof ContratoAceitoEvent evento
                        && evento.ipOrigem() == null
                        && evento.userAgentOrigem() == null));
    }

    @Test
    void executar_userAgentLongo_truncadoPorEntidade() {
        when(contratoRepository.findByIdForUpdate(contrato.getId())).thenReturn(Optional.of(contrato));
        when(aceiteRepository.existsByVersaoId(any())).thenReturn(false);
        when(contratoRepository.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aceiteRepository.save(any(AceiteContrato.class))).thenAnswer(inv -> inv.getArgument(0));

        String uaLongo = "U".repeat(700);
        useCase.executar(new RegistrarAceiteCommand(contrato.getId(), tomadorId, "127.0.0.1", uaLongo));

        verify(eventPublisher)
                .publishEvent(argThat((Object e) -> e instanceof ContratoAceitoEvent evento
                        && evento.userAgentOrigem().length() == 500));
    }
}
