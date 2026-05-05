package com.dynamis.sep_api.usuarios.web.dto;

import com.dynamis.sep_api.usuarios.domain.model.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UsuarioResponseDto(
        UUID id,
        String username,
        Role role,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataModificacao,
        String criadoPor,
        String modificadoPor) {}
