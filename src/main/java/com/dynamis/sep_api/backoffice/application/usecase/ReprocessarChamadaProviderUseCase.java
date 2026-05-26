package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.application.port.out.ProviderReprocessadorPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.application.service.AntiAbusoReprocessoService;
import com.dynamis.sep_api.backoffice.domain.event.ReprocessoDisparadoEvent;
import com.dynamis.sep_api.backoffice.domain.model.Reprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ReprocessoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Re-tenta chamada a provider externo (Sprint 14 Task 14.4). Dispatcher Strategy GoF escolhe a
 * implementacao por {@link TipoChamadaProvider}; tipo nao suportado eh
 * {@link UnsupportedOperationException} (mapeada para 400 em Task 14.7).
 *
 * <p>Ordem (fix code review Task 14.4): anti-abuso -&gt; adapter -&gt; criar Reprocesso ja com
 * status final -&gt; salvar 1 vez -&gt; publicar event. Tudo na mesma tx outer.
 */
@Service
public class ReprocessarChamadaProviderUseCase {

    private final ReprocessoRepository reprocessoRepository;
    private final ProviderReprocessadorPort providerReprocessador;
    private final AntiAbusoReprocessoService antiAbuso;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReprocessarChamadaProviderUseCase(
            ReprocessoRepository reprocessoRepository,
            ProviderReprocessadorPort providerReprocessador,
            AntiAbusoReprocessoService antiAbuso,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.reprocessoRepository = reprocessoRepository;
        this.providerReprocessador = providerReprocessador;
        this.antiAbuso = antiAbuso;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Reprocesso executar(TipoChamadaProvider tipo, UUID entidadeId, UUID disparadoPor, UUID itemId) {
        antiAbuso.validarLimite(entidadeId.toString());

        ResultadoReprocesso resultado = providerReprocessador.reprocessar(tipo, entidadeId);

        Reprocesso reprocesso = Reprocesso.paraProvider(itemId, tipo, entidadeId, OffsetDateTime.now(clock), disparadoPor);
        aplicarResultado(reprocesso, resultado);
        Reprocesso salvo = reprocessoRepository.save(reprocesso);

        eventPublisher.publishEvent(new ReprocessoDisparadoEvent(
                salvo.getId(), salvo.getTipo(), salvo.getIdentificadorExterno(), disparadoPor));
        return salvo;
    }

    private static void aplicarResultado(Reprocesso reprocesso, ResultadoReprocesso resultado) {
        if (resultado.status() == StatusReprocesso.SUCESSO) {
            reprocesso.concluirComSucesso(resultado.mensagemTecnica());
        } else {
            reprocesso.concluirComFalha(resultado.mensagemTecnica());
        }
    }
}
