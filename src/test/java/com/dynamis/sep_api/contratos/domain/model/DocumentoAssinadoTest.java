package com.dynamis.sep_api.contratos.domain.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentoAssinadoTest {

    private static final String HASH = "f".repeat(64);
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 5, 21, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String PATH = "00000000-0000-0000-0000-000000000001";

    @Test
    void criar_valido_persisteCampos() {
        UUID envelopeId = UUID.randomUUID();

        DocumentoAssinado d = DocumentoAssinado.criar(envelopeId, HASH, AGORA, "selo-x", PATH);

        assertThat(d.getEnvelopeId()).isEqualTo(envelopeId);
        assertThat(d.getHashSha256()).isEqualTo(HASH);
        assertThat(d.getDataAssinatura()).isEqualTo(AGORA);
        assertThat(d.getSelo()).isEqualTo("selo-x");
        assertThat(d.getPathStorage()).isEqualTo(PATH);
        assertThat(d.getId()).isNotNull();
    }

    @Test
    void criar_seloOpcional_aceitaNulo() {
        DocumentoAssinado d = DocumentoAssinado.criar(UUID.randomUUID(), HASH, AGORA, null, PATH);

        assertThat(d.getSelo()).isNull();
    }

    @Test
    void criar_hashInvalido_rejeita() {
        assertThatThrownBy(() -> DocumentoAssinado.criar(UUID.randomUUID(), "naohex", AGORA, null, PATH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hashSha256");
    }

    @Test
    void criar_hashUppercase_rejeita() {
        assertThatThrownBy(() -> DocumentoAssinado.criar(UUID.randomUUID(), "A".repeat(64), AGORA, null, PATH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void criar_pathStorageVazio_rejeita() {
        assertThatThrownBy(() -> DocumentoAssinado.criar(UUID.randomUUID(), HASH, AGORA, null, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pathStorage");
    }

    @Test
    void criar_camposObrigatoriosNulos_rejeita() {
        assertThatThrownBy(() -> DocumentoAssinado.criar(null, HASH, AGORA, null, PATH))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("envelopeId");
        assertThatThrownBy(() -> DocumentoAssinado.criar(UUID.randomUUID(), null, AGORA, null, PATH))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("hashSha256");
        assertThatThrownBy(() -> DocumentoAssinado.criar(UUID.randomUUID(), HASH, null, null, PATH))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dataAssinatura");
        assertThatThrownBy(() -> DocumentoAssinado.criar(UUID.randomUUID(), HASH, AGORA, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pathStorage");
    }
}
