package com.dynamis.sep_api.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Mapped superclass com os 4 campos de auditoria obrigatorios do PRD §15: {@code dataCriacao},
 * {@code dataModificacao}, {@code criadoPor}, {@code modificadoPor}.
 *
 * <p>Toda entidade persistida do dominio SEP deve estender esta classe. O {@link AuditorAwareImpl}
 * e responsavel por preencher {@code criadoPor} e {@code modificadoPor} com o UUID do usuario
 * autenticado (Sprint 3) ou fallback {@code "system"} quando nao houver autenticacao.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntidadeAuditavel {

    @CreatedDate
    @Column(name = "data_criacao", nullable = false, updatable = false)
    protected OffsetDateTime dataCriacao;

    @LastModifiedDate
    @Column(name = "data_modificacao", nullable = false)
    protected OffsetDateTime dataModificacao;

    @CreatedBy
    @Column(name = "criado_por", nullable = false, updatable = false, length = 50)
    protected String criadoPor;

    @LastModifiedBy
    @Column(name = "modificado_por", nullable = false, length = 50)
    protected String modificadoPor;

    public OffsetDateTime getDataCriacao() {
        return dataCriacao;
    }

    public OffsetDateTime getDataModificacao() {
        return dataModificacao;
    }

    public String getCriadoPor() {
        return criadoPor;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }
}
