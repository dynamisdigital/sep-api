package com.dynamis.sep_api.credores.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de leitura do status contratual de uma operacao da carteira credora (Sprint 17). */
public interface ConsultarContratoParaCarteiraCredoraPort {

    Optional<ContratoCarteiraView> consultarPorId(UUID contratoId);

    /**
     * Leitura em lote para a geracao de sugestoes de matching (Sprint 30) — uma consulta para N
     * contratos, sem N+1. Ids desconhecidos simplesmente nao aparecem no retorno.
     */
    List<ContratoCarteiraView> consultarPorIds(Collection<UUID> contratoIds);
}
