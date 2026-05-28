package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.port.out.ConsultarPropostasElegiveisParaCredoraPort;
import com.dynamis.sep_api.credores.application.port.out.PropostaElegivelView;
import com.dynamis.sep_api.credores.domain.model.OportunidadeInvestimento;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincroniza oportunidades de investimento a partir das propostas elegiveis (Sprint 17, Task 17.4).
 * Operacao administrativa explicita — {@code credores} nao assina eventos de outros modulos.
 * Idempotente: faz upsert por {@code propostaId} (1 oportunidade por proposta).
 */
@Service
public class SincronizarOportunidadesInvestimentoUseCase {

    private final ConsultarPropostasElegiveisParaCredoraPort propostasPort;
    private final OportunidadeInvestimentoRepository oportunidadeRepository;

    public SincronizarOportunidadesInvestimentoUseCase(
            ConsultarPropostasElegiveisParaCredoraPort propostasPort,
            OportunidadeInvestimentoRepository oportunidadeRepository) {
        this.propostasPort = propostasPort;
        this.oportunidadeRepository = oportunidadeRepository;
    }

    /** Retorna a quantidade de oportunidades criadas ou atualizadas. */
    @Transactional
    public int executar() {
        int sincronizadas = 0;
        for (PropostaElegivelView p : propostasPort.listarElegiveis()) {
            OportunidadeInvestimento oportunidade = oportunidadeRepository
                    .findByPropostaId(p.propostaId())
                    .orElseGet(() -> OportunidadeInvestimento.criar(
                            p.propostaId(), p.contratoId(), p.valor(), p.prazoMeses(), p.taxaJurosMensal()));
            oportunidade.atualizarSnapshot(p.contratoId(), p.valor(), p.prazoMeses(), p.taxaJurosMensal());
            oportunidadeRepository.save(oportunidade);
            sincronizadas++;
        }
        return sincronizadas;
    }
}
