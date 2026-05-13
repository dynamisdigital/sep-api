package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoVerificacaoKyc;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaInicioVerificacao;
import com.dynamis.sep_api.onboarding.application.port.out.dto.ResultadoKycProvider;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycRequest;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycResponse;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycResultadoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.util.List;

/** MapStruct mapper: traduz contratos do port {@link KycProvider}-side para DTOs Celcoin e vice-versa. */
@Mapper
public interface CelcoinKycMapper {

    @Named("toCelcoinRequest")
    default CelcoinKycRequest toCelcoinRequest(RequisicaoVerificacaoKyc requisicao) {
        List<CelcoinKycRequest.DocumentoRef> documentos = requisicao.documentos().stream()
                .map(d -> new CelcoinKycRequest.DocumentoRef(d.tipo().name(), d.sha256()))
                .toList();
        return new CelcoinKycRequest(
                requisicao.solicitacaoId().toString(),
                requisicao.cpf(),
                requisicao.nomeCompleto(),
                requisicao.dataNascimento(),
                documentos);
    }

    @Named("toRespostaInicio")
    default RespostaInicioVerificacao toRespostaInicio(CelcoinKycResponse celcoinResponse) {
        return new RespostaInicioVerificacao(celcoinResponse.idVerificacao(), celcoinResponse.status());
    }

    @Named("toResultadoKyc")
    default ResultadoKycProvider toResultadoKyc(CelcoinKycResultadoResponse response, String payloadCru) {
        StatusOnboarding statusFinal = mapearStatusFinal(response.status());
        return new ResultadoKycProvider(statusFinal, response.reason(), payloadCru);
    }

    static StatusOnboarding mapearStatusFinal(String statusCelcoin) {
        if (statusCelcoin == null) {
            return StatusOnboarding.PENDENCIA;
        }
        return switch (statusCelcoin.toUpperCase()) {
            case "APPROVED" -> StatusOnboarding.APROVADO;
            case "REJECTED" -> StatusOnboarding.REPROVADO;
            case "PENDING", "PROCESSING" -> StatusOnboarding.PENDENCIA;
            default -> StatusOnboarding.PENDENCIA;
        };
    }
}
