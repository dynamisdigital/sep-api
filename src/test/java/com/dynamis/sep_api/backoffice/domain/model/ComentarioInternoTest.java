package com.dynamis.sep_api.backoffice.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComentarioInternoTest {

    @Test
    void registrar_devolveComentarioValido() {
        UUID item = UUID.randomUUID();
        UUID autor = UUID.randomUUID();

        ComentarioInterno c = ComentarioInterno.registrar(item, autor, "obs operacional");

        assertThat(c.getId()).isNotNull();
        assertThat(c.getItemId()).isEqualTo(item);
        assertThat(c.getAutorId()).isEqualTo(autor);
        assertThat(c.getConteudo()).isEqualTo("obs operacional");
    }

    @Test
    void registrar_conteudoVazio_lanca() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ComentarioInterno.registrar(UUID.randomUUID(), UUID.randomUUID(), "  "));
    }

    @Test
    void registrar_conteudoAcima10k_lanca() {
        String longo = "x".repeat(10001);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ComentarioInterno.registrar(UUID.randomUUID(), UUID.randomUUID(), longo));
    }

    @Test
    void registrar_argumentoNulo_lanca() {
        assertThatThrownBy(() -> ComentarioInterno.registrar(null, UUID.randomUUID(), "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ComentarioInterno.registrar(UUID.randomUUID(), null, "x"))
                .isInstanceOf(NullPointerException.class);
    }
}
