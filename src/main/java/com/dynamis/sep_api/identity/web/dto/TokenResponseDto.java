package com.dynamis.sep_api.identity.web.dto;

import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT emitido apos login")
public record TokenResponseDto(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "3600") long expiresIn,
        UsuarioResponseDto usuario) {}
