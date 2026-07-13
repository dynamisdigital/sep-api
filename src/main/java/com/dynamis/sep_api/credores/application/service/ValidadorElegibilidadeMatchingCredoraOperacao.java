package com.dynamis.sep_api.credores.application.service;

import com.dynamis.sep_api.credores.domain.vo.CriterioMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.StatusCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;
import com.dynamis.sep_api.credores.domain.vo.StatusOperacaoFinanciada;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Avalia se um par (credora dona, operacao da propria carteira) e elegivel para sugestao de
 * matching assistido (Sprint 30 Task 30.1). Funcao pura sobre o {@link
 * CandidatoMatchingCredoraOperacao} ja carregado em lote pelos ports — nenhuma leitura aqui, sem
 * N+1. Regras, na ordem:
 *
 * <ol>
 *   <li>credora {@code ATIVA};
 *   <li>credora {@code ELEGIVEL};
 *   <li>operacao {@code ASSOCIADA} (ativa na carteira);
 *   <li>contrato da operacao {@code ASSINADO} (mesma fronteira do aporte, Sprint 29);
 *   <li>valor da operacao disponivel e positivo;
 *   <li>capacidade de aporte declarada comporta o valor (criterio nao aplicado quando o perfil nao
 *       declara capacidade);
 *   <li>par sem matching previo em qualquer status — REJEITADA tambem bloqueia, para o refresh nao
 *       re-sugerir par ja decidido pelo operador.
 * </ol>
 *
 * <p>O matching apenas sugere: nenhum aporte, Pix ou associacao e disparado automaticamente.
 */
@Service
public class ValidadorElegibilidadeMatchingCredoraOperacao {

    /** Mesma fronteira do aporte assistido (Sprint 29): somente contrato formalizado. */
    static final String STATUS_CONTRATO_ELEGIVEL = "ASSINADO";

    public ResultadoElegibilidadeMatching avaliar(CandidatoMatchingCredoraOperacao candidato) {
        List<CriterioMatchingCredoraOperacao> atendidos = new ArrayList<>();

        if (candidato.statusCredora() != StatusCredora.ATIVA) {
            return ResultadoElegibilidadeMatching.inelegivel(CriterioMatchingCredoraOperacao.CREDORA_ATIVA);
        }
        atendidos.add(CriterioMatchingCredoraOperacao.CREDORA_ATIVA);

        if (candidato.elegibilidadeCredora() != StatusElegibilidade.ELEGIVEL) {
            return ResultadoElegibilidadeMatching.inelegivel(CriterioMatchingCredoraOperacao.CREDORA_ELEGIVEL);
        }
        atendidos.add(CriterioMatchingCredoraOperacao.CREDORA_ELEGIVEL);

        if (candidato.statusOperacao() != StatusOperacaoFinanciada.ASSOCIADA) {
            return ResultadoElegibilidadeMatching.inelegivel(CriterioMatchingCredoraOperacao.OPERACAO_ATIVA);
        }
        atendidos.add(CriterioMatchingCredoraOperacao.OPERACAO_ATIVA);

        if (!STATUS_CONTRATO_ELEGIVEL.equals(candidato.statusContrato())) {
            return ResultadoElegibilidadeMatching.inelegivel(CriterioMatchingCredoraOperacao.CONTRATO_ASSINADO);
        }
        atendidos.add(CriterioMatchingCredoraOperacao.CONTRATO_ASSINADO);

        if (candidato.valorOperacao() == null || candidato.valorOperacao().signum() <= 0) {
            return ResultadoElegibilidadeMatching.inelegivel(CriterioMatchingCredoraOperacao.VALOR_OPERACAO_DISPONIVEL);
        }
        atendidos.add(CriterioMatchingCredoraOperacao.VALOR_OPERACAO_DISPONIVEL);

        if (candidato.capacidadeAporte() != null) {
            if (candidato.capacidadeAporte().compareTo(candidato.valorOperacao()) < 0) {
                return ResultadoElegibilidadeMatching.inelegivel(
                        CriterioMatchingCredoraOperacao.CAPACIDADE_COMPORTA_VALOR);
            }
            atendidos.add(CriterioMatchingCredoraOperacao.CAPACIDADE_COMPORTA_VALOR);
        }

        if (candidato.parComMatchingExistente()) {
            return ResultadoElegibilidadeMatching.inelegivel(CriterioMatchingCredoraOperacao.PAR_SEM_MATCHING_PREVIO);
        }
        atendidos.add(CriterioMatchingCredoraOperacao.PAR_SEM_MATCHING_PREVIO);

        return ResultadoElegibilidadeMatching.elegivel(atendidos);
    }
}
