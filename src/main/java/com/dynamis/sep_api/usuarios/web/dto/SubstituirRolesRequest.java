package com.dynamis.sep_api.usuarios.web.dto;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/** Substitui o conjunto de roles do usuario (Sprint 18). Conjunto deve ser nao vazio. */
public record SubstituirRolesRequest(@NotEmpty Set<Role> roles) {}
