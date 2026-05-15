package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.dto.HitPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinBackgroundCheckRequest;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinBackgroundCheckResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.util.List;

/** MapStruct mapper: traduz contratos do port {@code BackgroundCheckProvider}-side <-> Celcoin. */
@Mapper
public interface CelcoinBackgroundCheckMapper {

    @Named("toCelcoinRequest")
    default CelcoinBackgroundCheckRequest toCelcoinRequest(RequisicaoPld requisicao) {
        List<String> bases = requisicao.bases() == null
                ? List.of()
                : requisicao.bases().stream().map(BasePld::name).toList();
        return new CelcoinBackgroundCheckRequest(
                requisicao.solicitacaoId().toString(),
                requisicao.alvoTipo().name(),
                requisicao.documento(),
                requisicao.nome(),
                bases);
    }

    /**
     * Mapeia resposta Celcoin para {@link RespostaPld}. Apenas resultados com {@code hit=true}
     * viram {@link HitPld}; bases com {@code hit=false} sao descartadas do output (passagem limpa).
     * Resultados com base desconhecida sao ignorados.
     */
    @Named("toRespostaPld")
    default RespostaPld toRespostaPld(CelcoinBackgroundCheckResponse celcoin, String payloadCru) {
        if (celcoin == null || celcoin.resultados() == null) {
            return new RespostaPld(List.of(), payloadCru);
        }
        List<HitPld> hits = celcoin.resultados().stream()
                .filter(CelcoinBackgroundCheckResponse.ResultadoBase::hit)
                .map(r -> {
                    BasePld base = mapearBase(r.base());
                    return base == null
                            ? null
                            : new HitPld(
                                    base, r.motivo(), mapearSeveridade(r.severidade()), r.dataInclusao(), payloadCru);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return new RespostaPld(hits, payloadCru);
    }

    private static BasePld mapearBase(String base) {
        if (base == null) return null;
        return switch (base.toUpperCase()) {
            case "COAF" -> BasePld.COAF;
            case "OFAC" -> BasePld.OFAC;
            case "INTERPOL" -> BasePld.INTERPOL;
            case "MTE" -> BasePld.MTE;
            default -> null;
        };
    }

    private static SeveridadePld mapearSeveridade(String severidade) {
        if (severidade == null) return null;
        return switch (severidade.toUpperCase()) {
            case "BAIXA", "LOW" -> SeveridadePld.BAIXA;
            case "MEDIA", "MEDIUM", "MODERATE" -> SeveridadePld.MEDIA;
            case "ALTA", "HIGH", "CRITICAL" -> SeveridadePld.ALTA;
            default -> null;
        };
    }
}
