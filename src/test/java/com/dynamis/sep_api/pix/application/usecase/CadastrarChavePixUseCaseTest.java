package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.CadastrarChavePixCommand;
import com.dynamis.sep_api.pix.application.dto.CadastrarChavePixResult;
import com.dynamis.sep_api.pix.application.port.out.ContaOperacionalEscrowQueryPort;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoCadastrarChavePix;
import com.dynamis.sep_api.pix.application.port.out.dto.ContaOperacionalEscrowView;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCadastroChavePix;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.application.service.ChavePixSeguranca;
import com.dynamis.sep_api.pix.domain.event.PixChaveCadastradaEvent;
import com.dynamis.sep_api.pix.domain.model.ChavePix;
import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import com.dynamis.sep_api.pix.infrastructure.persistence.ChavePixRepository;
import com.dynamis.sep_api.shared.exception.ConflitoException;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CadastrarChavePixUseCaseTest {

    private static final String VALOR_BRUTO = "  Usuario@Empresa.COM ";
    private static final String VALOR_NORMALIZADO = "usuario@empresa.com";
    private static final String HASH = ChavePixSeguranca.hashHex(VALOR_NORMALIZADO);
    private static final String KEY = "idem-chave-1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-14T13:00:00Z"), ZoneOffset.UTC);

    private ChavePixRepository repository;
    private ContaOperacionalEscrowQueryPort contaPort;
    private PixProvider pixProvider;
    private ApplicationEventPublisher eventPublisher;
    private CadastrarChavePixUseCase useCase;

    private final UUID contaEscrowId = UUID.randomUUID();
    private final UUID operadorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(ChavePixRepository.class);
        contaPort = mock(ContaOperacionalEscrowQueryPort.class);
        pixProvider = mock(PixProvider.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new CadastrarChavePixUseCase(
                repository, contaPort, pixProvider, eventPublisher, CLOCK, mock(PlatformTransactionManager.class));

        when(contaPort.buscarContaOperacionalAtiva())
                .thenReturn(Optional.of(new ContaOperacionalEscrowView(contaEscrowId, "conta-tec-1")));
        when(repository.saveAndFlush(any(ChavePix.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pixProvider.cadastrarChave(any(), any(), any())).thenReturn(new RespostaCadastroChavePix("prov-key-1"));
    }

    private CadastrarChavePixCommand comando(String valor, String key) {
        return new CadastrarChavePixCommand(TipoChavePix.EMAIL, valor, key, operadorId, "corr-1");
    }

    private ChavePix chaveExistente(String valorNormalizado, String key) {
        return ChavePix.cadastrar(
                contaEscrowId,
                TipoChavePix.EMAIL,
                ChavePixSeguranca.hashHex(valorNormalizado),
                ChavePixSeguranca.mascarar(valorNormalizado),
                "prov-key-old",
                key,
                operadorId,
                OffsetDateTime.now(CLOCK));
    }

    @Test
    void cadastroValido_normalizaChamaProviderEPersisteMinimizado() {
        CadastrarChavePixResult resultado = useCase.executar(comando(VALOR_BRUTO, KEY));

        ArgumentCaptor<ComandoCadastrarChavePix> comandoProvider =
                ArgumentCaptor.forClass(ComandoCadastrarChavePix.class);
        verify(pixProvider).cadastrarChave(comandoProvider.capture(), eq(KEY), eq("corr-1"));
        assertThat(comandoProvider.getValue().valorNormalizado()).isEqualTo(VALOR_NORMALIZADO);
        assertThat(comandoProvider.getValue().contaTecnicaId()).isEqualTo("conta-tec-1");

        ArgumentCaptor<ChavePix> salva = ArgumentCaptor.forClass(ChavePix.class);
        verify(repository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getValorHash()).isEqualTo(HASH);
        assertThat(salva.getValue().getValorMascarado())
                .isNotEqualTo(VALOR_NORMALIZADO)
                .contains("*");
        assertThat(salva.getValue().getProviderKeyId()).isEqualTo("prov-key-1");
        assertThat(salva.getValue().getCriadaEm()).isEqualTo(OffsetDateTime.now(CLOCK));

        ArgumentCaptor<PixChaveCadastradaEvent> evento = ArgumentCaptor.forClass(PixChaveCadastradaEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().chaveId()).isEqualTo(salva.getValue().getId());
        assertThat(evento.getValue().operadorId()).isEqualTo(operadorId);

        assertThat(resultado.novo()).isTrue();
        assertThat(resultado.status()).isEqualTo(StatusChavePix.ATIVA);
        assertThat(resultado.valorMascarado()).isNotEqualTo(VALOR_NORMALIZADO);
    }

    @Test
    void duplicataAtiva_consultadaApenasNoStatusAtiva() {
        useCase.executar(comando(VALOR_BRUTO, KEY));

        // INATIVA nao bloqueia: a verificacao de duplicata olha somente chaves ATIVAS.
        verify(repository)
                .findByContaEscrowIdAndTipoAndValorHashAndStatus(
                        contaEscrowId, TipoChavePix.EMAIL, HASH, StatusChavePix.ATIVA);
    }

    @Test
    void replayMesmaKeyMesmoPayload_retornaExistenteSemProviderNemEvento() {
        ChavePix existente = chaveExistente(VALOR_NORMALIZADO, KEY);
        when(repository.findByContaEscrowIdAndIdempotencyKey(contaEscrowId, KEY))
                .thenReturn(Optional.of(existente));

        CadastrarChavePixResult resultado = useCase.executar(comando(VALOR_BRUTO, KEY));

        assertThat(resultado.novo()).isFalse();
        assertThat(resultado.id()).isEqualTo(existente.getId());
        verify(pixProvider, never()).cadastrarChave(any(), any(), any());
        verify(repository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void replayMesmaKeyPayloadDiferente_conflitaAntesDoProvider() {
        when(repository.findByContaEscrowIdAndIdempotencyKey(contaEscrowId, KEY))
                .thenReturn(Optional.of(chaveExistente("outra@empresa.com", KEY)));

        assertThatThrownBy(() -> useCase.executar(comando(VALOR_BRUTO, KEY)))
                .isInstanceOf(ConflitoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(VALOR_NORMALIZADO));
        verify(pixProvider, never()).cadastrarChave(any(), any(), any());
    }

    @Test
    void chaveAtivaEquivalenteComOutraKey_conflitaFuncional() {
        when(repository.findByContaEscrowIdAndTipoAndValorHashAndStatus(
                        contaEscrowId, TipoChavePix.EMAIL, HASH, StatusChavePix.ATIVA))
                .thenReturn(Optional.of(chaveExistente(VALOR_NORMALIZADO, "idem-anterior")));

        assertThatThrownBy(() -> useCase.executar(comando(VALOR_BRUTO, KEY)))
                .isInstanceOf(ConflitoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(VALOR_NORMALIZADO));
        verify(pixProvider, never()).cadastrarChave(any(), any(), any());
    }

    @Test
    void contaOperacionalAusente_falhaSanitizadaSemProvider() {
        when(contaPort.buscarContaOperacionalAtiva()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(comando(VALOR_BRUTO, KEY)))
                .isInstanceOf(OperacaoNaoProcessavelException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(VALOR_NORMALIZADO));
        verify(pixProvider, never()).cadastrarChave(any(), any(), any());
    }

    @Test
    void providerFalha_naoPersisteNemPublicaEvento() {
        when(pixProvider.cadastrarChave(any(), any(), any())).thenThrow(new PixProviderException("falha tecnica"));

        assertThatThrownBy(() -> useCase.executar(comando(VALOR_BRUTO, KEY))).isInstanceOf(PixProviderException.class);
        verify(repository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void corridaDeIdempotencyKey_convergeParaReplaySemQuinhentos() {
        ChavePix vencedora = chaveExistente(VALOR_NORMALIZADO, KEY);
        when(repository.findByContaEscrowIdAndIdempotencyKey(contaEscrowId, KEY))
                .thenReturn(Optional.empty(), Optional.of(vencedora));
        when(repository.saveAndFlush(any(ChavePix.class)))
                .thenThrow(new DataIntegrityViolationException("uq_chave_pix_conta_idempotency"));

        CadastrarChavePixResult resultado = useCase.executar(comando(VALOR_BRUTO, KEY));

        assertThat(resultado.novo()).isFalse();
        assertThat(resultado.id()).isEqualTo(vencedora.getId());
    }

    @Test
    void corridaDeChaveAtiva_convergeParaConflito() {
        when(repository.findByContaEscrowIdAndTipoAndValorHashAndStatus(
                        contaEscrowId, TipoChavePix.EMAIL, HASH, StatusChavePix.ATIVA))
                .thenReturn(Optional.empty(), Optional.of(chaveExistente(VALOR_NORMALIZADO, "idem-outra")));
        when(repository.saveAndFlush(any(ChavePix.class)))
                .thenThrow(new DataIntegrityViolationException("uq_chave_pix_ativa_por_valor"));

        assertThatThrownBy(() -> useCase.executar(comando(VALOR_BRUTO, KEY))).isInstanceOf(ConflitoException.class);
    }

    @Test
    void constraintDesconhecidaNaCorrida_propagaErroOriginal() {
        when(repository.saveAndFlush(any(ChavePix.class)))
                .thenThrow(new DataIntegrityViolationException("outra_constraint"));

        assertThatThrownBy(() -> useCase.executar(comando(VALOR_BRUTO, KEY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void idempotencyKeyInvalida_rejeitaSemResolverConta() {
        assertThatThrownBy(() -> useCase.executar(comando(VALOR_BRUTO, null))).isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(comando(VALOR_BRUTO, "  "))).isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(comando(VALOR_BRUTO, "k".repeat(101))))
                .isInstanceOf(ValidacaoException.class);
        verify(contaPort, never()).buscarContaOperacionalAtiva();
    }

    @Test
    void valorInvalido_rejeitaSemEcoarESemProvider() {
        assertThatThrownBy(() -> useCase.executar(comando("nao-e-email", KEY)))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("nao-e-email"));
        verify(pixProvider, never()).cadastrarChave(any(), any(), any());
    }

    @Test
    void comando_toStringNaoVazaValor() {
        assertThat(comando(VALOR_BRUTO, KEY).toString()).doesNotContain(VALOR_NORMALIZADO);
    }
}
