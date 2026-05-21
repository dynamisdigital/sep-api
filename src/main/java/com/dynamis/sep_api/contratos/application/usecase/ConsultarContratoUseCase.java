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
        Contrato contrato =
                repository.findById(contratoId).orElseThrow(() -> ContratoNaoEncontradoException.porId(contratoId));
        inicializarLazy(contrato);
        return contrato;
    }

    @Transactional(readOnly = true)
    public Contrato porPropostaId(UUID propostaId) {
        Contrato contrato = repository
                .findByPropostaId(propostaId)
                .orElseThrow(() -> ContratoNaoEncontradoException.porProposta(propostaId));
        inicializarLazy(contrato);
        return contrato;
    }

    /**
     * Forca a inicializacao das colecoes lazy dentro da tx readOnly. Necessario porque
     * {@code Contrato.versoes} e {@code VersaoContrato.clausulas} sao dois bags @OneToMany —
     * Hibernate nao consegue fetch ambos via {@code @EntityGraph} (MultipleBagFetchException).
     *
     * <p>Estrategia: 2 queries totais — Hibernate ja carregou {@code versoes} ao acessar o
     * agregado; aqui executamos {@code findByContratoIdComClausulas} que faz JOIN FETCH das
     * clausulas em uma unica query. Como estamos na mesma {@code PersistenceContext}, as
     * instancias retornadas sao as mesmas ja referenciadas pelo agregado — as colecoes lazy
     * dentro de cada {@code VersaoContrato} ficam inicializadas. Evita o N+1 que existia ao
     * iterar versoes e tocar {@code getClausulas().size()} em cada uma.
     */
    private void inicializarLazy(Contrato contrato) {
        if (!contrato.getVersoes().isEmpty()) {
            versaoRepository.findByContratoIdComClausulas(contrato.getId());
        }
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
