package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.domain.exception.ContratoNaoEncontradoException;
import com.dynamis.sep_api.contratos.domain.model.AceiteContrato;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.VersaoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.AceiteContratoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.VersaoContratoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta contrato por id ou por proposta (Sprint 10 Task 10.6). Caller (controller) e
 * responsavel por aplicar ownership/roles antes de chamar este use case — retorna o agregado cru.
 */
@Service
public class ConsultarContratoUseCase {

    private final ContratoRepository repository;
    private final VersaoContratoRepository versaoRepository;
    private final AceiteContratoRepository aceiteRepository;

    public ConsultarContratoUseCase(
            ContratoRepository repository,
            VersaoContratoRepository versaoRepository,
            AceiteContratoRepository aceiteRepository) {
        this.repository = repository;
        this.versaoRepository = versaoRepository;
        this.aceiteRepository = aceiteRepository;
    }

    @Transactional(readOnly = true)
    public Contrato porId(UUID contratoId) {
        return repository.findById(contratoId).orElseThrow(() -> ContratoNaoEncontradoException.porId(contratoId));
    }

    @Transactional(readOnly = true)
    public Contrato porPropostaId(UUID propostaId) {
        return repository
                .findByPropostaId(propostaId)
                .orElseThrow(() -> ContratoNaoEncontradoException.porProposta(propostaId));
    }

    @Transactional(readOnly = true)
    public List<VersaoContrato> listarVersoes(UUID contratoId) {
        if (!repository.existsById(contratoId)) {
            throw ContratoNaoEncontradoException.porId(contratoId);
        }
        return versaoRepository.findByContratoIdOrdenado(contratoId);
    }

    @Transactional(readOnly = true)
    public Optional<AceiteContrato> buscarAceiteDaVersaoVigente(Contrato contrato) {
        return contrato.versaoVigente().flatMap(v -> aceiteRepository.findByVersaoId(v.getId()));
    }
}
