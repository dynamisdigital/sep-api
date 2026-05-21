-- Sprint 10 Task 10.3 fix code review: idempotencia por parecerOrigemId.
--
-- versao_contrato.parecer_origem_id liga a versao gerada ao parecer (Sprint 8) que aprovou a
-- proposta e disparou a geracao. Se o mesmo PropostaAprovadaEvent for processado mais de uma
-- vez (replay), o GerarContratoUseCase detecta que ja existe versao com aquele parecer_origem
-- e retorna o contrato existente sem criar versao duplicada.
--
-- Coluna NULLABLE porque a Sprint 10 ainda nao bloqueia regeneracao manual via endpoint da
-- Task 10.6 (sem parecer associado nesse caminho); essa regeneracao manual cria versao sem
-- parecer_origem_id.

ALTER TABLE versao_contrato
    ADD COLUMN parecer_origem_id UUID NULL;

CREATE INDEX idx_versao_contrato_parecer_origem ON versao_contrato(parecer_origem_id)
    WHERE parecer_origem_id IS NOT NULL;
