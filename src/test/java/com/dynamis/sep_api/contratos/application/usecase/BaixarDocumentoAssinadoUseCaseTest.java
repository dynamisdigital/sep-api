package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.port.out.DocumentoAssinadoStorage;
import com.dynamis.sep_api.contratos.domain.exception.ContratoAssinaturaIndisponivelException;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.DocumentoAssinado;
import com.dynamis.sep_api.contratos.domain.model.EnvelopeAssinatura;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.DocumentoAssinadoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EnvelopeAssinaturaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaixarDocumentoAssinadoUseCaseTest {

    private static final String HASH = "1".repeat(64);
    private static final OffsetDateTime AGORA = OffsetDateTime.now();

    private ContratoLoaderService loader;
    private EnvelopeAssinaturaRepository envelopeRepository;
    private DocumentoAssinadoRepository documentoRepository;
    private DocumentoAssinadoStorage storage;
    private BaixarDocumentoAssinadoUseCase useCase;

    @BeforeEach
    void setUp() {
        loader = mock(ContratoLoaderService.class);
        envelopeRepository = mock(EnvelopeAssinaturaRepository.class);
        documentoRepository = mock(DocumentoAssinadoRepository.class);
        storage = mock(DocumentoAssinadoStorage.class);
        useCase = new BaixarDocumentoAssinadoUseCase(loader, envelopeRepository, documentoRepository, storage);
    }

    @Test
    void executar_assinado_retornaBytes() {
        Contrato contrato = contratoAssinado();
        EnvelopeAssinatura envelope = envelopeOf(contrato);
        DocumentoAssinado documento = DocumentoAssinado.criar(envelope.getId(), HASH, AGORA, "selo", "path-1");
        byte[] pdf = "%PDF".getBytes();

        when(loader.carregar(contrato.getId())).thenReturn(contrato);
        when(envelopeRepository.findByContratoId(contrato.getId())).thenReturn(Optional.of(envelope));
        when(documentoRepository.findByEnvelopeId(envelope.getId())).thenReturn(Optional.of(documento));
        when(storage.carregar("path-1")).thenReturn(Optional.of(pdf));

        BaixarDocumentoAssinadoUseCase.Resultado resultado = useCase.executar(contrato.getId());

        assertThat(resultado.conteudo()).isEqualTo(pdf);
        assertThat(resultado.documento().getHashSha256()).isEqualTo(HASH);
    }

    @Test
    void executar_contratoNaoAssinado_rejeita() {
        Contrato contrato = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.CCB);
        contrato.adicionarVersao("c", HASH);
        contrato.marcarAceito();
        when(loader.carregar(contrato.getId())).thenReturn(contrato);

        assertThatThrownBy(() -> useCase.executar(contrato.getId()))
                .isInstanceOf(ContratoAssinaturaIndisponivelException.class);
    }

    @Test
    void executar_blobPurgadoLgpd_diferencia() {
        Contrato contrato = contratoAssinado();
        EnvelopeAssinatura envelope = envelopeOf(contrato);
        String pathUuid = UUID.randomUUID().toString();
        DocumentoAssinado documento = DocumentoAssinado.criar(envelope.getId(), HASH, AGORA, null, pathUuid);

        when(loader.carregar(contrato.getId())).thenReturn(contrato);
        when(envelopeRepository.findByContratoId(contrato.getId())).thenReturn(Optional.of(envelope));
        when(documentoRepository.findByEnvelopeId(envelope.getId())).thenReturn(Optional.of(documento));
        when(storage.carregar(pathUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(contrato.getId()))
                .isInstanceOf(ContratoAssinaturaIndisponivelException.class)
                .hasMessageContaining("blob nao localizado");
    }

    @Test
    void executar_pathStorageCorrompido_diferencia() {
        Contrato contrato = contratoAssinado();
        EnvelopeAssinatura envelope = envelopeOf(contrato);
        DocumentoAssinado documento = DocumentoAssinado.criar(envelope.getId(), HASH, AGORA, null, "nao-uuid");

        when(loader.carregar(contrato.getId())).thenReturn(contrato);
        when(envelopeRepository.findByContratoId(contrato.getId())).thenReturn(Optional.of(envelope));
        when(documentoRepository.findByEnvelopeId(envelope.getId())).thenReturn(Optional.of(documento));
        when(storage.carregar("nao-uuid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(contrato.getId()))
                .isInstanceOf(ContratoAssinaturaIndisponivelException.class)
                .hasMessageContaining("formato invalido");
    }

    private Contrato contratoAssinado() {
        Contrato c = Contrato.criar(UUID.randomUUID(), UUID.randomUUID(), TipoContrato.CCB);
        c.adicionarVersao("c", HASH);
        c.marcarAceito();
        c.marcarEmAssinatura();
        c.marcarAssinado();
        return c;
    }

    private EnvelopeAssinatura envelopeOf(Contrato c) {
        return EnvelopeAssinatura.criar(
                c.getId(),
                c.versaoVigente().orElseThrow().getId(),
                "clicksign",
                "ext-1",
                c.getId() + ":v1",
                HASH,
                AGORA);
    }
}
