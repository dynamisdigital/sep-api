package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.dto.RepresentanteLegalProviderDto;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoKyb;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaKyb;
import com.dynamis.sep_api.onboarding.domain.vo.SituacaoCadastral;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKybRequest;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKybResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.util.List;

/** MapStruct mapper: traduz contratos do port {@code KybProvider}-side para DTOs Celcoin e vice-versa. */
@Mapper
public interface CelcoinKybMapper {

    @Named("toCelcoinRequest")
    default CelcoinKybRequest toCelcoinRequest(RequisicaoKyb requisicao) {
        List<CelcoinKybRequest.DocumentoRef> documentos = requisicao.documentos() == null
                ? List.of()
                : requisicao.documentos().stream()
                        .map(d -> new CelcoinKybRequest.DocumentoRef(d.tipo(), d.sha256()))
                        .toList();
        return new CelcoinKybRequest(
                requisicao.solicitacaoId().toString(),
                requisicao.cnpj(),
                requisicao.razaoSocialInformada(),
                documentos);
    }

    /**
     * Mapeia resposta Celcoin para {@link RespostaKyb}. Situacoes desconhecidas caem em
     * {@link SituacaoCadastral#DESCONHECIDA} pra explicitar incerteza e evitar progressao.
     */
    @Named("toRespostaKyb")
    default RespostaKyb toRespostaKyb(CelcoinKybResponse celcoin, String payloadCru) {
        SituacaoCadastral situacao = mapearSituacao(celcoin == null ? null : celcoin.situacao());
        if (celcoin == null) {
            return new RespostaKyb(situacao, null, null, null, null, null, null, List.of(), payloadCru);
        }
        List<RepresentanteLegalProviderDto> representantes = celcoin.representantes() == null
                ? List.of()
                : celcoin.representantes().stream()
                        .map(r -> new RepresentanteLegalProviderDto(r.nome(), r.cpf(), r.cargo()))
                        .toList();
        return new RespostaKyb(
                situacao,
                celcoin.razaoSocial(),
                celcoin.nomeFantasia(),
                celcoin.cnaePrincipal(),
                celcoin.cnaesSecundarios(),
                celcoin.capitalSocial(),
                celcoin.dataAbertura(),
                representantes,
                payloadCru);
    }

    private static SituacaoCadastral mapearSituacao(String situacao) {
        if (situacao == null) {
            return SituacaoCadastral.DESCONHECIDA;
        }
        return switch (situacao.toUpperCase()) {
            case "ACTIVE", "ATIVA" -> SituacaoCadastral.ATIVA;
            case "SUSPENDED", "SUSPENSA" -> SituacaoCadastral.SUSPENSA;
            case "INAPT", "INAPTA" -> SituacaoCadastral.INAPTA;
            case "TERMINATED", "BAIXADA" -> SituacaoCadastral.BAIXADA;
            default -> SituacaoCadastral.DESCONHECIDA;
        };
    }
}
