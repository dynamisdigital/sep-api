package com.dynamis.sep_api.usuarios.web.dto;

import com.dynamis.sep_api.usuarios.domain.model.Role;

import java.util.Set;

/** Conjunto de roles do usuario e a role principal (maior precedencia). */
public record RolesResponseDto(Set<Role> roles, Role principal) {}
