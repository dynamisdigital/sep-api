package com.dynamis.sep_api.governanca.application.dto;

import java.util.List;

/** Detalhe de um parametro operacional com seu historico de alteracoes (mais recente primeiro). */
public record ParametroComHistoricoView(ParametroOperacionalView parametro, List<VersaoParametroView> historico) {}
