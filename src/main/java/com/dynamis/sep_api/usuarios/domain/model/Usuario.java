package com.dynamis.sep_api.usuarios.domain.model;

import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
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

    /**
     * Role principal (denormalizada): mantida sincronizada com a role de maior precedencia em
     * {@link #roles}. Preserva compatibilidade com {@code getRole()} e claims/consumidores legados
     * ate a migracao completa para multi-role (Sprint 18 Task 18.3). A fonte autoritativa e
     * {@link #roles}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 40)
    private Role role;

    /** Conjunto autoritativo de roles cumulativas do usuario (Sprint 18). Nao vazio. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_role", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "role", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

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
        this.roles = EnumSet.of(role);
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

    /** Role principal (maior precedencia). Mantida para compatibilidade; ver {@link #getRoles()}. */
    public Role getRole() {
        return role;
    }

    /** Conjunto autoritativo de roles do usuario (somente leitura). */
    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public boolean possuiRole(Role role) {
        return roles.contains(role);
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

    /**
     * Altera o role do usuario (compatibilidade Sprint 8). Substitui o conjunto inteiro pela role
     * unica informada. Autorizacao (ADMIN + step-up) e auditoria ficam no use case chamador.
     */
    public void alterarRole(Role novaRole) {
        if (novaRole == null) {
            throw new IllegalArgumentException("novaRole obrigatoria");
        }
        substituirRoles(EnumSet.of(novaRole));
    }

    /**
     * Substitui o conjunto de roles (Sprint 18). Conjunto deve ser nao vazio. Autorizacao e
     * auditoria ficam no use case chamador.
     */
    public void substituirRoles(Set<Role> novasRoles) {
        if (novasRoles == null || novasRoles.isEmpty()) {
            throw new IllegalArgumentException("usuario deve possuir ao menos uma role");
        }
        // Mutacao in-place preserva a colecao gerenciada pelo Hibernate (@ElementCollection);
        // substituir a referencia quebraria o change-tracking da PersistentSet.
        this.roles.clear();
        this.roles.addAll(novasRoles);
        sincronizarPrincipal();
    }

    /** Adiciona uma role ao conjunto (cumulativo). No-op se ja presente. */
    public void adicionarRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("role obrigatoria");
        }
        this.roles.add(role);
        sincronizarPrincipal();
    }

    /** Remove uma role do conjunto. Lanca se for a ultima role (conjunto nao pode ficar vazio). */
    public void removerRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("role obrigatoria");
        }
        if (this.roles.size() == 1 && this.roles.contains(role)) {
            throw new IllegalArgumentException("usuario deve manter ao menos uma role");
        }
        this.roles.remove(role);
        sincronizarPrincipal();
    }

    private void sincronizarPrincipal() {
        this.role = Role.principalDe(this.roles);
    }
}
