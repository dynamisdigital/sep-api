package com.dynamis.sep_api.usuarios.web.mapper;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioMapperTest {

    private final UsuarioMapper mapper = Mappers.getMapper(UsuarioMapper.class);

    @Test
    void toResponseMapeiaCamposPublicosSemExporSenha() throws Exception {
        Usuario usuario = Usuario.criar("admin@sep.test", "hash-fake", Role.ADMIN);
        injetarAuditoria(usuario, OffsetDateTime.now(), "system");

        UsuarioResponseDto response = mapper.toResponse(usuario);

        assertThat(response.id()).isEqualTo(usuario.getId());
        assertThat(response.username()).isEqualTo("admin@sep.test");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.dataCriacao()).isNotNull();
        assertThat(response.dataModificacao()).isNotNull();
        assertThat(response.criadoPor()).isEqualTo("system");
        assertThat(response.modificadoPor()).isEqualTo("system");
        assertThat(UsuarioResponseDto.class.getRecordComponents())
                .noneMatch(rc -> rc.getName().equals("password"));
    }

    private void injetarAuditoria(Usuario usuario, OffsetDateTime quando, String quem) throws Exception {
        for (String nome : new String[] {"dataCriacao", "dataModificacao"}) {
            Field field = usuario.getClass().getSuperclass().getDeclaredField(nome);
            field.setAccessible(true);
            field.set(usuario, quando);
        }

        for (String nome : new String[] {"criadoPor", "modificadoPor"}) {
            Field field = usuario.getClass().getSuperclass().getDeclaredField(nome);
            field.setAccessible(true);
            field.set(usuario, quem);
        }
    }
}
