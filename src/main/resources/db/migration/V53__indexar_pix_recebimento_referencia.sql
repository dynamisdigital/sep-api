-- Sprint 26 (Gate P2 — leitura owner-scoped do status Pix da parcela): a consulta
-- findFirstByReferenciaIdOrderByDataCriacaoDesc busca o recebimento mais recente correlacionado a uma
-- referencia. A coluna referencia_id (adicionada em V51) nao possuia indice — cada leitura mobile faria
-- seq scan + sort sobre toda a pix_recebimento. Indice parcial composto: equality em referencia_id +
-- ordenacao por data_criacao; parcial porque referencia_id e nulo enquanto o recebimento nao e
-- correlacionado (NAO_IDENTIFICADO sem vinculo).
CREATE INDEX idx_pix_recebimento_referencia_data
    ON pix_recebimento (referencia_id, data_criacao DESC)
    WHERE referencia_id IS NOT NULL;
