package com.dynamis.sep_api.usuarios.web.mapper;

import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import org.mapstruct.Mapper;

/**
 * Mapeamento {@code Usuario} -> {@link UsuarioResponseDto} via MapStruct (ADR 0006).
 *
 * <p>NAO declara {@code toEntity}: o construtor de {@link Usuario} e {@code protected} por
 * design (factory {@link Usuario#criar(String, String, com.dynamis.sep_api.usuarios.domain.model.Role)}
 * encapsula geracao de UUID v6). Uso pelo {@code CriarUsuarioUseCase} segue a factory
 * apos hash BCrypt da senha.
 *
 * <p>{@code toResponse} produz record sem campo {@code password} — garantia estrutural
 * de que a senha jamais vaza em respostas.
 */
@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    UsuarioResponseDto toResponse(Usuario entity);
}
