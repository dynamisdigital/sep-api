package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.application.port.out.dto.RespostaEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.service.HashContratoService;
import com.dynamis.sep_api.contratos.application.service.ccb.CcbGenerator;
import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.EnvelopeAssinatura;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EnvelopeAssinaturaRepository;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
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

class EnviarParaAssinaturaUseCaseTest {

    private static final String HASH_VERSAO = "0".repeat(64);

    private ContratoLoaderService loader;
    private EnvelopeAssinaturaRepository envelopeRepository;
    private PropostaCreditoRepository propostaRepository;
    private UsuarioRepository usuarioRepository;
    private CcbGenerator ccbGenerator;
    private AssinaturaDigitalProvider provider;
    private HashContratoService hashService;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private EnviarParaAssinaturaUseCase useCase;

    private Contrato contrato;
    private PropostaCredito proposta;
    private Usuario tomador;

    @BeforeEach
    void setUp() {
        loader = mock(ContratoLoaderService.class);
        envelopeRepository = mock(EnvelopeAssinaturaRepository.class);
        propostaRepository = mock(PropostaCreditoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        ccbGenerator = mock(CcbGenerator.class);
        provider = mock(AssinaturaDigitalProvider.class);
        hashService = new HashContratoService();
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);

        useCase = new EnviarParaAssinaturaUseCase(
                loader,
                envelopeRepository,
                propostaRepository,
                usuarioRepository,
                ccbGenerator,
                provider,
                hashService,
                eventPublisher);

        proposta = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12);
        contrato = Contrato.criar(proposta.getId(), proposta.getTomadorId(), TipoContrato.CCB);
        contrato.adicionarVersao("conteudo", HASH_VERSAO);
        contrato.marcarAceito();
        tomador = Usuario.criar("tomador@example.com", "hash", Role.CLIENTE);
    }

    @Test
    void executar_contratoAceito_geraCcbEEnviaProvider() {
        when(loader.carregarComLock(contrato.getId())).thenReturn(contrato);
        when(envelopeRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(propostaRepository.findById(proposta.getId())).thenReturn(Optional.of(proposta));
        when(usuarioRepository.findById(any())).thenReturn(Optional.of(tomador));
        byte[] pdf = "%PDF fake".getBytes();
        when(ccbGenerator.gerar(any())).thenReturn(pdf);
        OffsetDateTime dataEnvio = OffsetDateTime.now();
        when(provider.enviarParaAssinatura(eq(pdf), any(), any()))
                .thenReturn(new RespostaEnvioAssinatura("ext-1", dataEnvio));
        when(envelopeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EnvelopeAssinatura envelope = useCase.executar(contrato.getId(), "corr");

        assertThat(envelope.getIdEnvelopeExterno()).isEqualTo("ext-1");
        assertThat(envelope.getIdempotencyKey()).isEqualTo(contrato.getId() + ":v1");
        assertThat(envelope.getHashPdfEnviado()).isEqualTo(hashService.calcular(pdf));
        assertThat(contrato.getStatus()).isEqualTo(StatusFormalizacao.EM_ASSINATURA);
        verify(envelopeRepository).save(any());
    }

    @Test
    void executar_envelopeJaExiste_idempotenteSemChamarProvider() {
        when(loader.carregarComLock(contrato.getId())).thenReturn(contrato);
        EnvelopeAssinatura existente = EnvelopeAssinatura.criar(
                contrato.getId(),
                contrato.versaoVigente().orElseThrow().getId(),
                "clicksign",
                "ext-existente",
                contrato.getId() + ":v1",
                HASH_VERSAO,
                OffsetDateTime.now());
        when(envelopeRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existente));

        EnvelopeAssinatura envelope = useCase.executar(contrato.getId(), "corr");

        assertThat(envelope).isSameAs(existente);
        verify(provider, never()).enviarParaAssinatura(any(), any(), any());
        verify(envelopeRepository, never()).save(any());
    }

    @Test
    void executar_contratoEmEstadoErrado_rejeita() {
        Contrato gerado = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.CCB);
        gerado.adicionarVersao("c", HASH_VERSAO);
        when(loader.carregarComLock(gerado.getId())).thenReturn(gerado);
        when(envelopeRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(gerado.getId(), "corr"))
                .isInstanceOf(ContratoEstadoInvalidoException.class);
        verify(provider, never()).enviarParaAssinatura(any(), any(), any());
    }
}
