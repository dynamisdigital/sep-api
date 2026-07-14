package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.ChavePixItemResult;
import com.dynamis.sep_api.pix.application.port.out.ContaOperacionalEscrowQueryPort;
import com.dynamis.sep_api.pix.domain.model.ChavePix;
import com.dynamis.sep_api.pix.infrastructure.persistence.ChavePixRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Listagem local das chaves Pix da conta operacional (Sprint 31 Task 31.6). Read-only: consulta
 * apenas a persistencia — <strong>nunca</strong> o provider. Inclui {@code ATIVA} e {@code INATIVA}
 * (historico), ordenadas por criacao decrescente (garantido pela consulta do repository). Conta
 * operacional ainda inexistente/inativa lista vazio — leitura nao falha.
 */
@Service
public class ListarChavesPixUseCase {

    private final ChavePixRepository repository;
    private final ContaOperacionalEscrowQueryPort contaPort;

    public ListarChavesPixUseCase(ChavePixRepository repository, ContaOperacionalEscrowQueryPort contaPort) {
        this.repository = repository;
        this.contaPort = contaPort;
    }

    @Transactional(readOnly = true)
    public List<ChavePixItemResult> executar() {
        return contaPort
                .buscarContaOperacionalAtiva()
                .map(conta -> repository.findAllByContaEscrowIdOrderByCriadaEmDesc(conta.contaEscrowId()).stream()
                        .map(ListarChavesPixUseCase::paraItem)
                        .toList())
                .orElse(List.of());
    }

    private static ChavePixItemResult paraItem(ChavePix chave) {
        return new ChavePixItemResult(
                chave.getId(),
                chave.getTipo(),
                chave.getValorMascarado(),
                chave.getStatus(),
                chave.getCriadaEm(),
                chave.getRemovidaEm());
    }
}
