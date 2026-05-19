package com.dynamis.sep_api.credito.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.credito.application.port.out.dto.MovimentacaoConsolidada;
import com.dynamis.sep_api.credito.application.port.out.dto.RequisicaoConsentimento;
import com.dynamis.sep_api.credito.application.port.out.dto.RespostaConsentimento;
import com.dynamis.sep_api.credito.infrastructure.adapter.celcoin.dto.CelcoinOpenFinanceConsentRequest;
import com.dynamis.sep_api.credito.infrastructure.adapter.celcoin.dto.CelcoinOpenFinanceConsentResponse;
import com.dynamis.sep_api.credito.infrastructure.adapter.celcoin.dto.CelcoinOpenFinanceMovimentacaoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Mapeamento entre DTOs do port e payload Celcoin/Finansystech (ADR 0006). Sprint 9.
 *
 * <p>{@code toMovimentacaoConsolidada} recebe payload bruto (string) capturado pelo adapter para
 * persistencia em audit/snapshot; mapper assume que esse payload ja vem sanitizado pelo provider
 * (sem dados identificaveis de conta) — caso a Celcoin real exponha extrato bruto, o adapter
 * deve agregar/anonimizar antes de chamar este metodo.
 */
@Mapper(componentModel = "spring")
public interface CelcoinOpenFinanceMapper {

    Logger LOG = LoggerFactory.getLogger(CelcoinOpenFinanceMapper.class);

    @Mapping(target = "propostaId", expression = "java(requisicao.propostaId().toString())")
    @Mapping(target = "tomadorId", expression = "java(requisicao.tomadorId().toString())")
    @Mapping(target = "documento", source = "cpfCnpjTomador")
    @Mapping(target = "redirectUri", source = "redirectUri")
    CelcoinOpenFinanceConsentRequest toCelcoinRequest(RequisicaoConsentimento requisicao);

    @Mapping(target = "idExterno", source = "idConsentimento")
    @Mapping(target = "urlAutorizacao", source = "urlAutorizacao")
    @Mapping(target = "dataExpiracao", source = "expiracao", qualifiedByName = "parseOffsetDateTime")
    RespostaConsentimento toRespostaConsentimento(CelcoinOpenFinanceConsentResponse celcoin);

    default MovimentacaoConsolidada toMovimentacaoConsolidada(
            CelcoinOpenFinanceMovimentacaoResponse celcoin, String payloadSanitizado) {
        return new MovimentacaoConsolidada(
                payloadSanitizado,
                celcoin.mediaEntradasMensal(),
                celcoin.mediaSaidasMensal(),
                celcoin.saldoMedio(),
                celcoin.mesesAvaliados());
    }

    @Named("parseOffsetDateTime")
    static OffsetDateTime parseOffsetDateTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso);
        } catch (DateTimeParseException ex) {
            // Celcoin payload com formato inesperado — null aceito; logamos pra triagem.
            LOG.warn("Celcoin Open Finance expires_at em formato invalido: '{}'", iso);
            return null;
        }
    }
}
