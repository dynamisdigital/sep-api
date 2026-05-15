package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.event.PldFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldLimpoEvent;
import com.dynamis.sep_api.onboarding.domain.model.ConsultaPld;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaPldRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.onboarding.web.dto.CelcoinPldCallbackRequest;
import com.dynamis.sep_api.shared.application.usecase.RegistrarWebhookEventUseCase;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessarCallbackPldUseCaseTest {

    private RegistrarWebhookEventUseCase registrarWebhookEventUseCase;
    private WebhookEventLogRepository webhookEventLogRepository;
    private SolicitacaoOnboardingRepository solicitacaoRepository;
    private KybEmpresaRepository kybRepository;
    private RepresentanteLegalRepository representanteRepository;
    private ConsultaPldRepository consultaPldRepository;
    private ApplicationEventPublisher eventPublisher;
    private ProcessarCallbackPldUseCase useCase;
    private SolicitacaoOnboarding pfAprovada;
    private WebhookEventLog evento;

    @BeforeEach
    void setup() {
        registrarWebhookEventUseCase = mock(RegistrarWebhookEventUseCase.class);
        webhookEventLogRepository = mock(WebhookEventLogRepository.class);
        solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        kybRepository = mock(KybEmpresaRepository.class);
        representanteRepository = mock(RepresentanteLegalRepository.class);
        consultaPldRepository = mock(ConsultaPldRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new ProcessarCallbackPldUseCase(
                registrarWebhookEventUseCase,
                webhookEventLogRepository,
                solicitacaoRepository,
                kybRepository,
                representanteRepository,
                consultaPldRepository,
                eventPublisher);

        pfAprovada = SolicitacaoOnboarding.criarPessoa(
                UUID.randomUUID(), new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1));
        pfAprovada.registrarDocumentoEnviado();
        pfAprovada.marcarEmVerificacao("ext");
        pfAprovada.finalizar(StatusOnboarding.APROVADO);

        evento = WebhookEventLog.registrar("celcoin-pld", "callback", "idem", "sig", "{}");
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(webhookEventLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(evento));
        when(solicitacaoRepository.findById(pfAprovada.getId())).thenReturn(Optional.of(pfAprovada));
        when(solicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CelcoinPldCallbackRequest payloadLimpo() {
        return new CelcoinPldCallbackRequest(
                pfAprovada.getId().toString(),
                List.of(new CelcoinPldCallbackRequest.AlvoResultado(
                        "PESSOA",
                        "52998224725",
                        List.of(
                                new CelcoinPldCallbackRequest.BaseResultado("COAF", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("OFAC", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("INTERPOL", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("MTE", false, null, null, null)))));
    }

    private CelcoinPldCallbackRequest payloadComHit() {
        return new CelcoinPldCallbackRequest(
                pfAprovada.getId().toString(),
                List.of(new CelcoinPldCallbackRequest.AlvoResultado(
                        "PESSOA",
                        "52998224725",
                        List.of(
                                new CelcoinPldCallbackRequest.BaseResultado("COAF", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado(
                                        "OFAC", true, "Sancao", "HIGH", LocalDate.of(2024, 1, 1)),
                                new CelcoinPldCallbackRequest.BaseResultado("INTERPOL", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("MTE", false, null, null, null)))));
    }

    @Test
    void duplicadoIdempotenteRetornaResultadoSemReprocessar() {
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false);

        ProcessarCallbackPldUseCase.Resultado r = useCase.executar("idem", "sig", "{}", payloadLimpo());

        assertThat(r.duplicado()).isTrue();
        verify(consultaPldRepository, never()).save(any());
    }

    @Test
    void pldLimpoMovePraAprovadoFinalEEmitePldLimpoEventPerAlvo() {
        useCase.executar("idem-1", "sig", "{}", payloadLimpo());

        assertThat(pfAprovada.getStatus()).isEqualTo(StatusOnboarding.APROVADO_FINAL);
        verify(consultaPldRepository, org.mockito.Mockito.times(4)).save(any(ConsultaPld.class));
        ArgumentCaptor<Object> evtCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(evtCaptor.capture());
        List<Object> eventos = evtCaptor.getAllValues();
        assertThat(eventos).anyMatch(e -> e instanceof PldLimpoEvent);
        assertThat(eventos)
                .anyMatch(
                        e -> e instanceof PldFinalizadoEvent pf && pf.statusFinal() == StatusOnboarding.APROVADO_FINAL);
    }

    @Test
    void hitMovePraReprovadoPld() {
        useCase.executar("idem-2", "sig", "{}", payloadComHit());

        assertThat(pfAprovada.getStatus()).isEqualTo(StatusOnboarding.REPROVADO_PLD);
    }

    @Test
    void payloadVazioMarcaFalhouSemTransicaoDeStatus() {
        CelcoinPldCallbackRequest vazio =
                new CelcoinPldCallbackRequest(pfAprovada.getId().toString(), List.of());

        useCase.executar("idem-vazio", "sig", "{}", vazio);

        assertThat(pfAprovada.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
        verify(eventPublisher, never()).publishEvent(any());
        verify(consultaPldRepository, never()).save(any());
    }

    @Test
    void payloadComBaseFaltandoMarcaFalhou() {
        CelcoinPldCallbackRequest incompleto = new CelcoinPldCallbackRequest(
                pfAprovada.getId().toString(),
                List.of(new CelcoinPldCallbackRequest.AlvoResultado(
                        "PESSOA",
                        "52998224725",
                        // Falta MTE
                        List.of(
                                new CelcoinPldCallbackRequest.BaseResultado("COAF", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("OFAC", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("INTERPOL", false, null, null, null)))));

        useCase.executar("idem-base-faltando", "sig", "{}", incompleto);

        assertThat(pfAprovada.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void payloadComAlvoSemDatabasesMarcaFalhouSemNPE() {
        CelcoinPldCallbackRequest semBases = new CelcoinPldCallbackRequest(
                pfAprovada.getId().toString(),
                List.of(new CelcoinPldCallbackRequest.AlvoResultado("PESSOA", "52998224725", null)));

        useCase.executar("idem-sem-bases", "sig", "{}", semBases);

        assertThat(pfAprovada.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
        verify(consultaPldRepository, never()).save(any());
    }

    @Test
    void pjSemAlvoEmpresaMarcaFalhou() {
        SolicitacaoOnboarding pj = SolicitacaoOnboarding.criarEmpresa(UUID.randomUUID(), "11222333000181", "ACME");
        pj.registrarDocumentoEnviado();
        pj.marcarEmVerificacao("ext");
        pj.finalizar(StatusOnboarding.APROVADO);
        when(solicitacaoRepository.findById(pj.getId())).thenReturn(Optional.of(pj));
        when(kybRepository.findBySolicitacaoId(pj.getId())).thenReturn(Optional.empty());

        // PJ payload sem EMPRESA — so um representante
        CelcoinPldCallbackRequest payload = new CelcoinPldCallbackRequest(
                pj.getId().toString(),
                List.of(new CelcoinPldCallbackRequest.AlvoResultado(
                        "REPRESENTANTE",
                        "52998224725",
                        List.of(
                                new CelcoinPldCallbackRequest.BaseResultado("COAF", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("OFAC", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("INTERPOL", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("MTE", false, null, null, null)))));

        useCase.executar("idem-sem-empresa", "sig", "{}", payload);

        assertThat(pj.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void payloadComDocumentoDiferenteDaSolicitacaoPfMarcaFalhou() {
        // CPF do alvo != CPF persistido na solicitacao (52998224725)
        CelcoinPldCallbackRequest payload = new CelcoinPldCallbackRequest(
                pfAprovada.getId().toString(),
                List.of(new CelcoinPldCallbackRequest.AlvoResultado(
                        "PESSOA",
                        "11144477735", // CPF de outra pessoa
                        List.of(
                                new CelcoinPldCallbackRequest.BaseResultado("COAF", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("OFAC", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("INTERPOL", false, null, null, null),
                                new CelcoinPldCallbackRequest.BaseResultado("MTE", false, null, null, null)))));

        useCase.executar("idem-pf-doc-errado", "sig", "{}", payload);

        assertThat(pfAprovada.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
        verify(consultaPldRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void payloadPjComCnpjErradoMarcaFalhou() {
        SolicitacaoOnboarding pj = pjAprovadaComKyb("11222333000181", "52998224725");
        // Payload PJ traz CNPJ diferente do KYB persistido
        CelcoinPldCallbackRequest payload = new CelcoinPldCallbackRequest(
                pj.getId().toString(),
                List.of(
                        new CelcoinPldCallbackRequest.AlvoResultado(
                                "EMPRESA",
                                "27865757000102", // CNPJ alheio
                                quatroBasesLimpas()),
                        new CelcoinPldCallbackRequest.AlvoResultado(
                                "REPRESENTANTE", "52998224725", quatroBasesLimpas())));

        useCase.executar("idem-pj-cnpj-errado", "sig", "{}", payload);

        assertThat(pj.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
        verify(consultaPldRepository, never()).save(any());
    }

    @Test
    void payloadComTargetTypeDesconhecidoMarcaFalhou() {
        CelcoinPldCallbackRequest payload = new CelcoinPldCallbackRequest(
                pfAprovada.getId().toString(),
                List.of(new CelcoinPldCallbackRequest.AlvoResultado("ESTRANHO", "52998224725", quatroBasesLimpas())));

        useCase.executar("idem-tipo-desconhecido", "sig", "{}", payload);

        assertThat(pfAprovada.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
        verify(consultaPldRepository, never()).save(any());
    }

    @Test
    void payloadPjComDocumentoExtraAlemDosEsperadosMarcaFalhou() {
        SolicitacaoOnboarding pj = pjAprovadaComKyb("11222333000181", "52998224725");

        CelcoinPldCallbackRequest payload = new CelcoinPldCallbackRequest(
                pj.getId().toString(),
                List.of(
                        new CelcoinPldCallbackRequest.AlvoResultado("EMPRESA", "11222333000181", quatroBasesLimpas()),
                        new CelcoinPldCallbackRequest.AlvoResultado(
                                "REPRESENTANTE", "52998224725", quatroBasesLimpas()),
                        // CPF de representante alheio nao deveria estar no payload
                        new CelcoinPldCallbackRequest.AlvoResultado(
                                "REPRESENTANTE", "11144477735", quatroBasesLimpas())));

        useCase.executar("idem-pj-extra", "sig", "{}", payload);

        assertThat(pj.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
        verify(consultaPldRepository, never()).save(any());
    }

    private List<CelcoinPldCallbackRequest.BaseResultado> quatroBasesLimpas() {
        return List.of(
                new CelcoinPldCallbackRequest.BaseResultado("COAF", false, null, null, null),
                new CelcoinPldCallbackRequest.BaseResultado("OFAC", false, null, null, null),
                new CelcoinPldCallbackRequest.BaseResultado("INTERPOL", false, null, null, null),
                new CelcoinPldCallbackRequest.BaseResultado("MTE", false, null, null, null));
    }

    /** Cria PJ APROVADO + KybEmpresa + 1 representante e plugga nos mocks. */
    private SolicitacaoOnboarding pjAprovadaComKyb(String cnpj, String cpfRepresentante) {
        SolicitacaoOnboarding pj = SolicitacaoOnboarding.criarEmpresa(UUID.randomUUID(), cnpj, "ACME LTDA");
        pj.registrarDocumentoEnviado();
        pj.marcarEmVerificacao("ext");
        pj.finalizar(StatusOnboarding.APROVADO);
        KybEmpresa kyb =
                KybEmpresa.criar(pj.getId(), new Cnpj(cnpj), "ACME", null, TipoSocietario.LTDA, PorteEmpresa.ME);
        RepresentanteLegal rep = RepresentanteLegal.criar(kyb.getId(), "Joao", new Cpf(cpfRepresentante), "Diretor");
        when(solicitacaoRepository.findById(pj.getId())).thenReturn(Optional.of(pj));
        when(kybRepository.findBySolicitacaoId(pj.getId())).thenReturn(Optional.of(kyb));
        when(representanteRepository.findByKybEmpresaId(kyb.getId())).thenReturn(List.of(rep));
        return pj;
    }

    @Test
    void solicitacaoJaFinalizadaTrataComoDuplicadoTardio() {
        SolicitacaoOnboarding ja = SolicitacaoOnboarding.criarPessoa(
                UUID.randomUUID(), new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1));
        ja.registrarDocumentoEnviado();
        ja.marcarEmVerificacao("ext");
        ja.finalizar(StatusOnboarding.APROVADO);
        ja.marcarAprovadoFinal();
        when(solicitacaoRepository.findById(ja.getId())).thenReturn(Optional.of(ja));

        useCase.executar(
                "idem-tardio",
                "sig",
                "{}",
                new CelcoinPldCallbackRequest(ja.getId().toString(), List.of()));

        verify(eventPublisher, never()).publishEvent(any());
        verify(consultaPldRepository, never()).save(any());
    }
}
