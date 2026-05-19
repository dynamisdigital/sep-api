package com.dynamis.sep_api.credito.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.credito.application.port.out.OpenFinanceProvider;
import com.dynamis.sep_api.credito.application.port.out.dto.MovimentacaoConsolidada;
import com.dynamis.sep_api.credito.application.port.out.dto.RequisicaoConsentimento;
import com.dynamis.sep_api.credito.application.port.out.dto.RespostaConsentimento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Adapter fake do {@link OpenFinanceProvider} para dev/test sem credenciais Celcoin/Finansystech.
 * Sprint 9.
 *
 * <p>Cenarios padrao:
 *
 * <ul>
 *   <li>{@code iniciarConsentimento} retorna URL e id externo deterministicos por proposta;
 *   <li>{@code consultarMovimentacao} retorna snapshot consolidado com movimentacao alta padrao
 *       (R$ 10.000 entradas / R$ 7.000 saidas / R$ 3.000 saldo medio / 6 meses).
 * </ul>
 *
 * <p>Cenarios alternativos (movimentacao baixa, saldo negativo, sem dados) devem ser configurados
 * via mock injection em testes especificos de use case — fake nao implementa toggle por properties
 * pra manter dev/test simples e deterministico.
 *
 * <p>Ativado quando {@code app.open-finance.provider=fake} (default em {@code application.yml} de
 * dev e test). Substitui automaticamente o {@link CelcoinOpenFinanceProvider} via
 * {@link ConditionalOnProperty}.
 */
@Component
@ConditionalOnProperty(name = "app.open-finance.provider", havingValue = "fake", matchIfMissing = true)
public class FakeOpenFinanceProvider implements OpenFinanceProvider {

    private static final Logger log = LoggerFactory.getLogger(FakeOpenFinanceProvider.class);
    private static final String URL_AUTORIZACAO_TEMPLATE = "https://fake-open-finance.sep.test/authorize/%s";

    @Override
    public RespostaConsentimento iniciarConsentimento(RequisicaoConsentimento requisicao, String correlationId) {
        String idExterno = "fake-of-" + requisicao.propostaId();
        String url = URL_AUTORIZACAO_TEMPLATE.formatted(idExterno);
        log.info(
                "FakeOpenFinanceProvider.iniciarConsentimento propostaId={} correlationId={} -> {}",
                requisicao.propostaId(),
                correlationId,
                idExterno);
        return new RespostaConsentimento(idExterno, url, OffsetDateTime.now().plusDays(30));
    }

    @Override
    public MovimentacaoConsolidada consultarMovimentacao(String idExternoConsentimento, String correlationId) {
        log.info(
                "FakeOpenFinanceProvider.consultarMovimentacao idExterno={} correlationId={} -> snapshot alto",
                idExternoConsentimento,
                correlationId);
        String payload = "{\"id_externo\":\"" + idExternoConsentimento + "\",\"fonte\":\"fake\"}";
        return new MovimentacaoConsolidada(
                payload, new BigDecimal("10000.00"), new BigDecimal("7000.00"), new BigDecimal("3000.00"), 6);
    }
}
