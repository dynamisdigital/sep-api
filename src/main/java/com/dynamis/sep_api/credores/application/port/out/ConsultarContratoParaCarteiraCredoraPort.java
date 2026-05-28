package com.dynamis.sep_api.credores.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Porta de leitura do status contratual de uma operacao da carteira credora (Sprint 17). */
public interface ConsultarContratoParaCarteiraCredoraPort {

    Optional<ContratoCarteiraView> consultarPorId(UUID contratoId);
}
