package com.dynamis.sep_api.usuarios.domain.model;

import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Usuario do sistema SEP. Materializa a tabela {@code usuario} entregue pelas migrations V1+V6.
 *
 * <p>A senha deve chegar hashada; esta entidade nao deve receber segredo em texto claro.
 *
 * <p>Sprint 5 Task 5.5 adicionou {@code precisaRedefinirSenha} e {@code mfaHabilitado}.
 */
@Entity
@Table(name = "usuario")
public class Usuario extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 255)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 40)
    private Role role;

    @Column(name = "precisa_redefinir_senha", nullable = false)
    private boolean precisaRedefinirSenha;

    @Column(name = "mfa_habilitado", nullable = false)
    private boolean mfaHabilitado;

    protected Usuario() {
        // requerido pelo Hibernate
    }

    private Usuario(UUID id, String username, String password, Role role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.precisaRedefinirSenha = false;
        this.mfaHabilitado = false;
    }

    public static Usuario criar(String username, String passwordHash, Role role) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new Usuario(id, username, passwordHash, role);
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public boolean isPrecisaRedefinirSenha() {
        return precisaRedefinirSenha;
    }

    public boolean isMfaHabilitado() {
        return mfaHabilitado;
    }

    /** Substitui o hash da senha. Recebe sempre hash BCrypt — nunca senha em texto claro. */
    public void alterarSenha(String novoPasswordHash) {
        this.password = novoPasswordHash;
        this.precisaRedefinirSenha = false;
    }

    public void marcarMfaHabilitado() {
        this.mfaHabilitado = true;
    }

    public void marcarMfaDesabilitado() {
        this.mfaHabilitado = false;
    }
}
