package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.EnvelopeAssinatura;
import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EnvelopeAssinaturaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso: retorna snapshot do status da assinatura para um contrato (Sprint 11 Task 11.5).
 * Fonte: estado local do {@link EnvelopeAssinatura}; webhook (Task 11.6) eh a fonte de verdade
 * operacional — consultar o provider externo aqui esta fora de escopo nesta sprint (registrado
 * como follow-up em CONTRATOS.md).
 */
@Service
public class ConsultarStatusAssinaturaUseCase {

    private final ContratoLoaderService contratoLoader;
    private final EnvelopeAssinaturaRepository envelopeRepository;

    public ConsultarStatusAssinaturaUseCase(
            ContratoLoaderService contratoLoader, EnvelopeAssinaturaRepository envelopeRepository) {
        this.contratoLoader = contratoLoader;
        this.envelopeRepository = envelopeRepository;
    }

    @Transactional(readOnly = true)
    public StatusAssinaturaContrato executar(UUID contratoId) {
        Objects.requireNonNull(contratoId, "contratoId obrigatorio");
        Contrato contrato = contratoLoader.carregar(contratoId);
        Optional<EnvelopeAssinatura> envelope = envelopeRepository.findByContratoId(contratoId);
        return new StatusAssinaturaContrato(
                contrato.getStatus(),
                envelope.map(EnvelopeAssinatura::getStatus).orElse(null),
                envelope.map(EnvelopeAssinatura::getIdEnvelopeExterno).orElse(null),
                envelope.map(EnvelopeAssinatura::getDataAtualizacaoProvider).orElse(null));
    }

    public record StatusAssinaturaContrato(
            StatusFormalizacao statusContrato,
            StatusEnvelope statusEnvelope,
            String idEnvelopeExterno,
            OffsetDateTime dataAtualizacaoProvider) {}
}
