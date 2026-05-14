package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.KybProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RepresentanteLegalProviderDto;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoKyb;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaKyb;
import com.dynamis.sep_api.onboarding.domain.vo.SituacaoCadastral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Adapter fake do {@link KybProvider} pra dev/test sem credenciais Celcoin. Default retorna CNPJ
 * {@code ATIVA} com dados cadastrais minimos e um representante legal deterministico baseado no
 * CNPJ informado.
 *
 * <p>Ativado quando {@code app.kyb.provider=fake} (default em dev/test). Substitui automaticamente
 * o {@link CelcoinKybProvider} via {@link ConditionalOnProperty}.
 *
 * <p>Pra simular {@code SUSPENSA}/{@code INAPTA} em testes, mockar o port no nivel da camada
 * {@code application}.
 */
@Component
@ConditionalOnProperty(name = "app.kyb.provider", havingValue = "fake", matchIfMissing = true)
public class FakeKybProvider implements KybProvider {

    private static final Logger log = LoggerFactory.getLogger(FakeKybProvider.class);

    @Override
    public RespostaKyb consultarCnpj(RequisicaoKyb requisicao, String correlationId) {
        log.info(
                "FakeKybProvider.consultarCnpj solicitacaoId={} cnpj=*** correlationId={} -> ATIVA",
                requisicao.solicitacaoId(),
                correlationId);
        String payload = "{\"registration_status\":\"ACTIVE\",\"tax_id\":\"" + requisicao.cnpj() + "\"}";
        return new RespostaKyb(
                SituacaoCadastral.ATIVA,
                requisicao.razaoSocialInformada(),
                null,
                "62.01-5-01",
                null,
                new BigDecimal("100000.00"),
                LocalDate.of(2010, 1, 1),
                List.of(new RepresentanteLegalProviderDto("Representante Fake", "52998224725", "Diretor")),
                payload);
    }
}
