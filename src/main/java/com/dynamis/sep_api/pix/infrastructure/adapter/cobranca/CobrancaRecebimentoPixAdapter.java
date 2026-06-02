package com.dynamis.sep_api.pix.infrastructure.adapter.cobranca;

import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoCommand;
import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoResult;
import com.dynamis.sep_api.cobranca.application.usecase.RegistrarRecebimentoUseCase;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.pix.application.port.out.CobrancaRecebimentoPixPort;
import com.dynamis.sep_api.pix.application.port.out.dto.RecebimentoPixCobrancaResult;
import com.dynamis.sep_api.pix.application.port.out.dto.RegistrarRecebimentoPixCobrancaCommand;
import org.springframework.stereotype.Component;

/**
 * Adapter que traduz {@link CobrancaRecebimentoPixPort} para o caminho oficial de baixa de
 * {@code cobranca} (Sprint 21 Task 21.4): delega ao {@link RegistrarRecebimentoUseCase}, que detem o
 * lock pessimista, o calculo de valor devido, a criacao do {@code Recebimento}, o status da parcela e
 * a movimentacao escrow idempotente. O {@code pix} so envia/recebe DTOs proprios.
 *
 * <p>{@code meioPagamento} fixo {@code "PIX"} distingue a origem do recebimento na cobranca.
 */
@Component
public class CobrancaRecebimentoPixAdapter implements CobrancaRecebimentoPixPort {

    private static final String MEIO_PAGAMENTO_PIX = "PIX";

    private final RegistrarRecebimentoUseCase registrarRecebimentoUseCase;

    public CobrancaRecebimentoPixAdapter(RegistrarRecebimentoUseCase registrarRecebimentoUseCase) {
        this.registrarRecebimentoUseCase = registrarRecebimentoUseCase;
    }

    @Override
    public RecebimentoPixCobrancaResult registrarRecebimento(RegistrarRecebimentoPixCobrancaCommand comando) {
        RegistrarRecebimentoResult resultado = registrarRecebimentoUseCase.executar(new RegistrarRecebimentoCommand(
                comando.parcelaId(),
                comando.valorRecebido(),
                comando.dataRecebimento(),
                MEIO_PAGAMENTO_PIX,
                comando.identificadorExterno(),
                comando.idempotencyKey(),
                null,
                comando.registradoPor()));
        return new RecebimentoPixCobrancaResult(
                resultado.recebimentoId(), resultado.statusParcela() == StatusParcela.PAGA, resultado.novo());
    }
}
