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

    /**
     * Mapeia resposta Celcoin para o sealed {@link ResultadoKycProvider}.
     *
     * <ul>
     *   <li>{@code APPROVED} -> {@code Finalizado(APROVADO)}
     *   <li>{@code REJECTED} -> {@code Finalizado(REPROVADO)}
     *   <li>{@code PENDING} -> {@code Finalizado(PENDENCIA)} (pendencia final ≠ ainda processando)
     *   <li>{@code PROCESSING} e desconhecidos -> {@code EmAndamento}
     * </ul>
     */
    @Named("toResultadoKyc")
    default ResultadoKycProvider toResultadoKyc(CelcoinKycResultadoResponse response, String payloadCru) {
        if (response == null || response.status() == null) {
            return new ResultadoKycProvider.EmAndamento(payloadCru);
        }
        return switch (response.status().toUpperCase()) {
            case "APPROVED" -> new ResultadoKycProvider.Finalizado(
                    StatusOnboarding.APROVADO, response.reason(), payloadCru);
            case "REJECTED" -> new ResultadoKycProvider.Finalizado(
                    StatusOnboarding.REPROVADO, response.reason(), payloadCru);
            case "PENDING" -> new ResultadoKycProvider.Finalizado(
                    StatusOnboarding.PENDENCIA, response.reason(), payloadCru);
            default -> new ResultadoKycProvider.EmAndamento(payloadCru);
        };
    }
}
