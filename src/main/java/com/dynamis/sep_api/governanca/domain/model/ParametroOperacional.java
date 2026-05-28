package com.dynamis.sep_api.governanca.domain.model;

import com.dynamis.sep_api.governanca.domain.exception.ValorParametroInvalidoException;
import com.dynamis.sep_api.governanca.domain.vo.TipoParametroOperacional;
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
 * Parametro operacional governado e versionado (Sprint 18, Epic 11). Valor armazenado como texto
 * e validado conforme {@link TipoParametroOperacional}. Cada alteracao incrementa {@link #versao}
 * e gera uma {@link VersaoParametroOperacional} (historico), registrada pelo use case.
 */
@Entity
@Table(name = "parametro_operacional")
public class ParametroOperacional extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "chave", nullable = false, unique = true, updatable = false, length = 120)
    private String chave;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20, updatable = false)
    private TipoParametroOperacional tipo;

    @Column(name = "valor", nullable = false, length = 500)
    private String valor;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "versao", nullable = false)
    private int versao;

    protected ParametroOperacional() {
        // requerido pelo Hibernate
    }

    private ParametroOperacional(UUID id, String chave, TipoParametroOperacional tipo, String valor, String descricao) {
        this.id = id;
        this.chave = chave;
        this.tipo = tipo;
        this.valor = valor;
        this.descricao = descricao;
        this.ativo = true;
        this.versao = 1;
    }

    public static ParametroOperacional criar(
            String chave, TipoParametroOperacional tipo, String valor, String descricao) {
        if (chave == null || chave.isBlank()) {
            throw new IllegalArgumentException("chave obrigatoria");
        }
        if (!tipo.aceita(valor)) {
            throw new ValorParametroInvalidoException(chave, valor, tipo.name());
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ParametroOperacional(id, chave, tipo, valor, descricao);
    }

    /** Altera o valor (validando o tipo) e incrementa a versao. Retorna o valor anterior. */
    public String alterarValor(String novoValor) {
        if (!tipo.aceita(novoValor)) {
            throw new ValorParametroInvalidoException(chave, novoValor, tipo.name());
        }
        String anterior = this.valor;
        this.valor = novoValor;
        this.versao++;
        return anterior;
    }

    public UUID getId() {
        return id;
    }

    public String getChave() {
        return chave;
    }

    public TipoParametroOperacional getTipo() {
        return tipo;
    }

    public String getValor() {
        return valor;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public int getVersao() {
        return versao;
    }
}
