package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.BackgroundCheckProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.HitPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Adapter fake do {@link BackgroundCheckProvider} pra dev/test sem credenciais Celcoin. Default
 * retorna limpo em todas as bases — modo seguro pra fluxo feliz.
 *
 * <p>Pra simular hit em testes, registre documentos via {@link #marcarDocumentoComoHit(String)}
 * antes da consulta. Estado e estatico/global no JVM da suite; chamar {@link #limparHits()} entre
 * cenarios pra evitar contaminacao.
 */
@Component
@ConditionalOnProperty(name = "app.pld.provider", havingValue = "fake", matchIfMissing = true)
public class FakeBackgroundCheckProvider implements BackgroundCheckProvider {

    private static final Logger log = LoggerFactory.getLogger(FakeBackgroundCheckProvider.class);

    private static final Set<String> DOCUMENTOS_COM_HIT = new CopyOnWriteArraySet<>();

    /** Marca um documento (CPF ou CNPJ normalizado) pra retornar hit em todas as bases consultadas. */
    public static void marcarDocumentoComoHit(String documento) {
        DOCUMENTOS_COM_HIT.add(documento);
    }

    /** Limpa todas as marcacoes — chamar entre cenarios de teste. */
    public static void limparHits() {
        DOCUMENTOS_COM_HIT.clear();
    }

    @Override
    public RespostaPld consultarPessoa(RequisicaoPld requisicao, String correlationId) {
        return consultar("PESSOA", requisicao, correlationId);
    }

    @Override
    public RespostaPld consultarEmpresa(RequisicaoPld requisicao, String correlationId) {
        return consultar("EMPRESA", requisicao, correlationId);
    }

    private RespostaPld consultar(String tipo, RequisicaoPld requisicao, String correlationId) {
        boolean hit = DOCUMENTOS_COM_HIT.contains(requisicao.documento());
        log.info(
                "FakeBackgroundCheckProvider.{} solicitacaoId={} correlationId={} hit={}",
                tipo,
                requisicao.solicitacaoId(),
                correlationId,
                hit);
        Set<BasePld> bases = requisicao.bases() == null ? Set.of() : requisicao.bases();
        if (!hit) {
            return new RespostaPld(List.of(), bases, "{\"hit\":false}");
        }
        List<HitPld> hits = bases.stream()
                .map(b -> new HitPld(b, "Fake hit", SeveridadePld.ALTA, LocalDate.now(), "{\"hit\":true}"))
                .toList();
        return new RespostaPld(hits, bases, "{\"hit\":true}");
    }
}
