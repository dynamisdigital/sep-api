package com.dynamis.sep_api.identity.web.dto;

import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;

public record TokenResponseDto(String accessToken, String tokenType, long expiresIn, UsuarioResponseDto usuario) {}
