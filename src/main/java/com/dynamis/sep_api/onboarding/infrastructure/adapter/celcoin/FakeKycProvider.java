package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.KycProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoVerificacaoKyc;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaInicioVerificacao;
import com.dynamis.sep_api.onboarding.application.port.out.dto.ResultadoKycProvider;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter fake do {@link KycProvider} para dev/test sem credenciais Celcoin. Sempre retorna {@code
 * APROVADO}.
 *
 * <p>Ativado quando {@code app.kyc.provider=fake} (default em {@code application.yml} de dev e
 * test). Substitui automaticamente o {@link CelcoinKycProvider} via {@link ConditionalOnProperty}.
 */
@Component
@ConditionalOnProperty(name = "app.kyc.provider", havingValue = "fake", matchIfMissing = true)
public class FakeKycProvider implements KycProvider {

    private static final Logger log = LoggerFactory.getLogger(FakeKycProvider.class);

    @Override
    public RespostaInicioVerificacao iniciarVerificacao(RequisicaoVerificacaoKyc requisicao, String correlationId) {
        String idExterno = "fake-" + requisicao.solicitacaoId();
        log.info(
                "FakeKycProvider.iniciarVerificacao solicitacaoId={} correlationId={} -> {}",
                requisicao.solicitacaoId(),
                correlationId,
                idExterno);
        return new RespostaInicioVerificacao(idExterno, "PROCESSING");
    }

    @Override
    public ResultadoKycProvider consultarResultado(String idVerificacaoExterna, String correlationId) {
        log.info(
                "FakeKycProvider.consultarResultado idVerificacao={} correlationId={} -> APROVADO",
                idVerificacaoExterna,
                correlationId);
        String payload = "{\"verification_id\":\"" + idVerificacaoExterna + "\",\"status\":\"APPROVED\"}";
        return new ResultadoKycProvider(StatusOnboarding.APROVADO, null, payload);
    }
}
