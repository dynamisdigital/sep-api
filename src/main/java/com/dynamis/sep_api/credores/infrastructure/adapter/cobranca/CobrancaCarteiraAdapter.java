package com.dynamis.sep_api.credores.infrastructure.adapter.cobranca;

import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.AgendaPagamentoRepository;
import com.dynamis.sep_api.credores.application.port.out.CarteiraCobrancaResumo;
import com.dynamis.sep_api.credores.application.port.out.ConsultarCobrancaParaCarteiraCredoraPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter de leitura que agrega a cobranca de um contrato em {@link CarteiraCobrancaResumo} para a
 * carteira credora (Sprint 17). Expoe apenas numeros agregados; nenhum dado sensivel do tomador.
 */
@Component
public class CobrancaCarteiraAdapter implements ConsultarCobrancaParaCarteiraCredoraPort {

    private static final Set<StatusParcela> ATRASADAS = Set.of(StatusParcela.ATRASADA, StatusParcela.INADIMPLENTE);
    private static final Set<StatusParcela> ENCERRADAS = Set.of(StatusParcela.PAGA, StatusParcela.RENEGOCIADA);

    private final AgendaPagamentoRepository agendaRepository;

    public CobrancaCarteiraAdapter(AgendaPagamentoRepository agendaRepository) {
        this.agendaRepository = agendaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CarteiraCobrancaResumo> resumoPorContrato(UUID contratoId) {
        return agendaRepository.findByContratoIdAndAtivaTrue(contratoId).map(this::resumir);
    }

    private CarteiraCobrancaResumo resumir(AgendaPagamento agenda) {
        int pagas = 0;
        int atrasadas = 0;
        BigDecimal totalRecebido = BigDecimal.ZERO;
        LocalDate proximoVencimento = null;

        for (ParcelaCobranca parcela : agenda.getParcelas()) {
            StatusParcela status = parcela.getStatus();
            if (status == StatusParcela.PAGA) {
                pagas++;
            }
            if (ATRASADAS.contains(status)) {
                atrasadas++;
            }
            for (Recebimento recebimento : parcela.getRecebimentos()) {
                totalRecebido = totalRecebido.add(recebimento.getValorRecebido());
            }
            if (!ENCERRADAS.contains(status)
                    && (proximoVencimento == null || parcela.getDataVencimento().isBefore(proximoVencimento))) {
                proximoVencimento = parcela.getDataVencimento();
            }
        }

        return new CarteiraCobrancaResumo(
                agenda.getNumeroParcelas(), agenda.getValorTotal(), pagas, atrasadas, totalRecebido, proximoVencimento);
    }
}
