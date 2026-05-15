package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.port.out.KybProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RepresentanteLegalProviderDto;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoKyb;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaKyb;
import com.dynamis.sep_api.onboarding.domain.event.KybFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.model.ConsultaCNPJ;
import com.dynamis.sep_api.onboarding.domain.model.DocumentoCadastral;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.SituacaoCadastral;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaCNPJRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.DocumentoCadastralRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IniciarVerificacaoKybUseCaseTest {

    private static final String CNPJ_VALIDO = "11222333000181";

    private SolicitacaoOnboardingRepository solicitacaoRepository;
    private KybEmpresaRepository kybRepository;
    private ConsultaCNPJRepository consultaCnpjRepository;
    private RepresentanteLegalRepository representanteRepository;
    private DocumentoCadastralRepository documentoRepository;
    private KybProvider kybProvider;
    private ApplicationEventPublisher eventPublisher;
    private IniciarVerificacaoKybUseCase useCase;
    private UUID usuarioId;
    private SolicitacaoOnboarding solicitacao;
    private KybEmpresa kyb;

    @BeforeEach
    void setup() {
        solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        kybRepository = mock(KybEmpresaRepository.class);
        consultaCnpjRepository = mock(ConsultaCNPJRepository.class);
        representanteRepository = mock(RepresentanteLegalRepository.class);
        documentoRepository = mock(DocumentoCadastralRepository.class);
        kybProvider = mock(KybProvider.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new IniciarVerificacaoKybUseCase(
                solicitacaoRepository,
                kybRepository,
                consultaCnpjRepository,
                representanteRepository,
                documentoRepository,
                kybProvider,
                eventPublisher);

        usuarioId = UUID.randomUUID();
        solicitacao = SolicitacaoOnboarding.criarEmpresa(usuarioId, CNPJ_VALIDO, "ACME LTDA");
        solicitacao.registrarDocumentoEnviado();
        kyb = KybEmpresa.criar(
                solicitacao.getId(), new Cnpj(CNPJ_VALIDO), "ACME LTDA", null, TipoSocietario.LTDA, PorteEmpresa.MEDIO);

        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(kybRepository.findBySolicitacaoId(solicitacao.getId())).thenReturn(Optional.of(kyb));
        when(solicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kybRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentoRepository.findBySolicitacaoId(solicitacao.getId()))
                .thenReturn(List.of(
                        documentoFake(TipoDocumento.CONTRATO_SOCIAL, "sha-contrato"),
                        documentoFake(TipoDocumento.COMPROVANTE_ENDERECO, "sha-endereco")));
    }

    private DocumentoCadastral documentoFake(TipoDocumento tipo, String sha256) {
        return DocumentoCadastral.criar(
                solicitacao.getId(), tipo, new byte[] {1, 2, 3}, "application/pdf", "doc.pdf", sha256);
    }

    @Test
    void situacaoAtivaPersisteConsultaERepresentantesEFinalizaAprovado() {
        when(kybProvider.consultarCnpj(any(), anyString()))
                .thenReturn(new RespostaKyb(
                        SituacaoCadastral.ATIVA,
                        "ACME Industria LTDA",
                        "ACME",
                        "62.01-5-01",
                        null,
                        null,
                        null,
                        List.of(new RepresentanteLegalProviderDto("Joao", "52998224725", "CEO")),
                        "{\"ok\":true}"));

        SolicitacaoOnboarding resultado = useCase.executar(solicitacao.getId(), usuarioId, false, "corr-1");

        assertThat(resultado.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
        verify(consultaCnpjRepository).save(any(ConsultaCNPJ.class));
        verify(representanteRepository).save(any(RepresentanteLegal.class));
        ArgumentCaptor<KybFinalizadoEvent> evtCaptor = ArgumentCaptor.forClass(KybFinalizadoEvent.class);
        verify(eventPublisher).publishEvent(evtCaptor.capture());
        assertThat(evtCaptor.getValue().statusFinal()).isEqualTo(StatusOnboarding.APROVADO);

        ArgumentCaptor<RequisicaoKyb> reqCaptor = ArgumentCaptor.forClass(RequisicaoKyb.class);
        verify(kybProvider).consultarCnpj(reqCaptor.capture(), anyString());
        assertThat(reqCaptor.getValue().documentos()).hasSize(2);
        assertThat(reqCaptor.getValue().documentos())
                .extracting(RequisicaoKyb.DocumentoMetadadosKyb::tipo)
                .containsExactlyInAnyOrder("CONTRATO_SOCIAL", "COMPROVANTE_ENDERECO");
    }

    @Test
    void aceitaCcmeiAlternativoAoContratoSocial() {
        when(documentoRepository.findBySolicitacaoId(solicitacao.getId()))
                .thenReturn(List.of(
                        documentoFake(TipoDocumento.CCMEI, "sha-ccmei"),
                        documentoFake(TipoDocumento.COMPROVANTE_ENDERECO, "sha-endereco")));
        when(kybProvider.consultarCnpj(any(), anyString()))
                .thenReturn(new RespostaKyb(
                        SituacaoCadastral.ATIVA, "ACME MEI", null, null, null, null, null, List.of(), "{}"));

        SolicitacaoOnboarding resultado = useCase.executar(solicitacao.getId(), usuarioId, false, "corr-1b");

        assertThat(resultado.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
    }

    @Test
    void situacaoSuspensaReprovaSemPersistirRepresentantes() {
        when(kybProvider.consultarCnpj(any(), anyString()))
                .thenReturn(new RespostaKyb(
                        SituacaoCadastral.SUSPENSA, null, null, null, null, null, null, List.of(), "{}"));

        SolicitacaoOnboarding resultado = useCase.executar(solicitacao.getId(), usuarioId, false, "corr-2");

        assertThat(resultado.getStatus()).isEqualTo(StatusOnboarding.REPROVADO);
        verify(consultaCnpjRepository).save(any(ConsultaCNPJ.class));
        verify(representanteRepository, never()).save(any());
        ArgumentCaptor<KybFinalizadoEvent> evtCaptor = ArgumentCaptor.forClass(KybFinalizadoEvent.class);
        verify(eventPublisher).publishEvent(evtCaptor.capture());
        assertThat(evtCaptor.getValue().statusFinal()).isEqualTo(StatusOnboarding.REPROVADO);
    }

    @Test
    void rejeitaSolicitacaoPF() {
        SolicitacaoOnboarding pf = SolicitacaoOnboarding.criarPessoa(
                usuarioId,
                new com.dynamis.sep_api.onboarding.domain.vo.Cpf("52998224725"),
                "Joao",
                java.time.LocalDate.of(1990, 1, 1));
        when(solicitacaoRepository.findById(pf.getId())).thenReturn(Optional.of(pf));

        assertThatThrownBy(() -> useCase.executar(pf.getId(), usuarioId, false, "corr-3"))
                .isInstanceOf(ValidacaoException.class);
    }

    @Test
    void rejeitaUsuarioNaoOwner() {
        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), UUID.randomUUID(), false, "corr-4"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejeitaSemDocumentosMinimos() {
        when(documentoRepository.findBySolicitacaoId(solicitacao.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), usuarioId, false, "corr-5"))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("Documentos minimos PJ");

        verify(kybProvider, never()).consultarCnpj(any(), anyString());
    }

    @Test
    void rejeitaSemDocumentoSocietario() {
        when(documentoRepository.findBySolicitacaoId(solicitacao.getId()))
                .thenReturn(List.of(documentoFake(TipoDocumento.COMPROVANTE_ENDERECO, "sha-endereco")));

        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), usuarioId, false, "corr-6"))
                .isInstanceOf(ValidacaoException.class);
    }

    @Test
    void rejeitaSemComprovanteEndereco() {
        when(documentoRepository.findBySolicitacaoId(solicitacao.getId()))
                .thenReturn(List.of(documentoFake(TipoDocumento.CONTRATO_SOCIAL, "sha-contrato")));

        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), usuarioId, false, "corr-7"))
                .isInstanceOf(ValidacaoException.class);
    }
}
