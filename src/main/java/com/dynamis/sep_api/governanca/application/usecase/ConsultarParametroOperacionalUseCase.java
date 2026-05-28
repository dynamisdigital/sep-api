package com.dynamis.sep_api.governanca.application.usecase;

import com.dynamis.sep_api.governanca.application.dto.ParametroComHistoricoView;
import com.dynamis.sep_api.governanca.application.dto.ParametroOperacionalView;
import com.dynamis.sep_api.governanca.application.dto.VersaoParametroView;
import com.dynamis.sep_api.governanca.domain.exception.ParametroOperacionalNaoEncontradoException;
import com.dynamis.sep_api.governanca.domain.model.ParametroOperacional;
import com.dynamis.sep_api.governanca.infrastructure.persistence.ParametroOperacionalRepository;
import com.dynamis.sep_api.governanca.infrastructure.persistence.VersaoParametroOperacionalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta o detalhe de um parametro operacional e seu historico de alteracoes (Sprint 18). */
@Service
public class ConsultarParametroOperacionalUseCase {

    private final ParametroOperacionalRepository parametroRepository;
    private final VersaoParametroOperacionalRepository versaoRepository;

    public ConsultarParametroOperacionalUseCase(
            ParametroOperacionalRepository parametroRepository, VersaoParametroOperacionalRepository versaoRepository) {
        this.parametroRepository = parametroRepository;
        this.versaoRepository = versaoRepository;
    }

    @Transactional(readOnly = true)
    public ParametroComHistoricoView executar(String chave) {
        ParametroOperacional parametro = parametroRepository
                .findByChave(chave)
                .orElseThrow(() -> new ParametroOperacionalNaoEncontradoException(chave));
        var historico = versaoRepository.findByParametroIdOrderByVersaoDesc(parametro.getId()).stream()
                .map(VersaoParametroView::de)
                .toList();
        return new ParametroComHistoricoView(ParametroOperacionalView.de(parametro), historico);
    }
}
