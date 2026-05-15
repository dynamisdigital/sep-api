# Politica de PLD — Onboarding SEP

Politica e cuidados tecnicos de **Prevencao a Lavagem de Dinheiro** aplicada no modulo
`onboarding`. Implementacao consolidada na Sprint 7 (PF + PJ + representantes legais).

Visao geral do fluxo, endpoints, providers e webhooks PLD em
[`ONBOARDING.md`](./ONBOARDING.md). Este documento foca em **politica regulatoria**,
**exposicao publica/retencao** e **checklist para revisao juridica**.

> Status: **PENDENTE revisao juridica**. Conteudo abaixo reflete a interpretacao tecnica
> atual da Resolucao CMN 4.656/2018, Lei 9.613/1998 e Circular BCB 3.978/2020. Deve ser
> validado por compliance antes da promocao para producao.

## 1. Bases consultadas (4 obrigatorias)

| Base | Origem | Significado |
| ---- | ------ | ----------- |
| `COAF` | Conselho de Controle de Atividades Financeiras | Comunicacao de operacoes suspeitas, listas restritivas internas. |
| `OFAC` | Office of Foreign Assets Control (US Treasury) | SDN list — sancoes economicas internacionais. |
| `INTERPOL` | International Criminal Police Organization | Notices vermelhas/azuis — investigados/procurados internacionais. |
| `MTE` | Ministerio do Trabalho e Emprego | Cadastro de empregadores que submeteram trabalhadores a condicoes analogas a escravidao ("lista suja"). |

Modelo: `RequisicaoPld.comBasesObrigatorias(solicitacaoId, alvoTipo, nome, documento)`
sempre constroi com as 4 bases. Validacao do callback Celcoin rejeita payload sem as 4.

Adicionar base requer:
1. Atualizar enum `BasePld`.
2. Migration ampliando `chk_consulta_pld_base`.
3. Atualizar `RequisicaoPld.comBasesObrigatorias`.
4. Atualizar validacao do callback (`ProcessarCallbackPldUseCase`).
5. Stub Celcoin no mapper.
6. Revisao juridica + atualizacao deste documento.

## 2. Alvos por solicitacao

| Solicitacao | Alvos consultados |
| ----------- | ----------------- |
| PF (`TipoSolicitante.PESSOA`)  | 1 alvo `AlvoPld.PESSOA` — o proprio CPF. |
| PJ (`TipoSolicitante.EMPRESA`) | 1 alvo `AlvoPld.EMPRESA` (CNPJ) + N alvos `AlvoPld.REPRESENTANTE` (CPF de cada representante legal informado pelo `KybProvider`). |

Status PLD individual por representante e persistido em `representante_legal.status_pld`
(`PENDENTE`/`LIMPO`/`HIT`) com `data_consulta_pld`. Consulta detalhada (1 registro por
base obrigatoria) fica em `consulta_pld`.

## 3. Criterio de bloqueio

**Qualquer hit em qualquer base, em qualquer alvo, bloqueia o onboarding inteiro**:

- KYC/KYB `APROVADO` -> PLD dispara automatico (via `PldOrchestrationListener`).
- Hit em pelo menos 1 base de pelo menos 1 alvo -> `solicitacao.status = REPROVADO_PLD`.
- Todos os alvos limpos em todas as bases obrigatorias -> `solicitacao.status =
  APROVADO_FINAL`.

Severidade declarada (`BAIXA`/`MEDIA`/`ALTA`/null) **nao** entra no criterio — qualquer
hit bloqueia. Severidade fica registrada para apoio ao processo de excecao manual
(Sprint 14+ — fora de escopo Sprint 7).

Re-execucao do PLD a partir de `APROVADO_FINAL`/`REPROVADO_PLD` e **idempotente** —
`IniciarPldPessoa/EmpresaUseCase` faz early-return; webhook tardio idem.

## 4. Como hits sao armazenados

### Tabela `consulta_pld` (V11)

1 registro por `(solicitacao_id, alvo_tipo, alvo_documento, base)` — total de 4 registros
por alvo (1 por base obrigatoria), independente de hit ou limpo. Layout:

| Coluna | Tipo | Visivel publicamente? |
| ------ | ---- | --------------------- |
| `id` | UUID | nao |
| `solicitacao_id` | UUID (FK sem CASCADE) | nao |
| `alvo_tipo` | `AlvoPld` enum | sim (publico — `PESSOA`/`EMPRESA`/`REPRESENTANTE`) |
| `alvo_documento` | string 14 (CPF/CNPJ normalizado) | **NAO — somente auditoria interna** |
| `base` | `BasePld` enum | sim (publico — `COAF`/`OFAC`/`INTERPOL`/`MTE`) |
| `hit` | boolean | sim (publico — agregado) |
| `motivo` | text | **NAO — somente auditoria interna** |
| `severidade` | `SeveridadePld` enum (nullable) | **NAO** |
| `data_inclusao` | date (nullable) | **NAO** |
| `payload_provider` | jsonb | **NAO — payload bruto restrito** |
| `data_consulta` | timestamptz | sim (uso interno + auditoria) |
| `retencao_ate` | date (`data_consulta + 5 anos`) | nao |

### Tabela `audit_log_seguranca` (eventos PLD)

Trilha auditavel reforcada com `tipo` ∈ {`PLD_INICIADO`, `PLD_HIT_DETECTADO`,
`PLD_LIMPO`, `PLD_FINALIZADO`} (V13). Detalhes JSON sao **sanitizados** antes da
gravacao:

- documento sempre mascarado (`529******25`).
- motivo truncado em 200 chars + sentinel `NAO_INFORMADO` para null/blank.
- severidade nula vira sentinel `NAO_INFORMADA`.
- payload bruto do provider, dados completos de representante, CPF/CNPJ inteiros,
  binarios — **NUNCA** gravados.

Listener: `OnboardingAuditListener` com `@TransactionalEventListener(AFTER_COMMIT)` +
`@Transactional(REQUIRES_NEW)` — auditoria nao quebra fluxo de negocio e nao participa
de rollback do publicador.

## 5. O que NUNCA e exposto publicamente

| Item | Onde vive | Quem pode ler |
| ---- | --------- | ------------- |
| CPF/CNPJ completo do alvo PLD | `consulta_pld.alvo_documento`, `representante_legal.cpf` | Auditoria interna, banco de dados — JAMAIS API REST publica. |
| Motivo do hit | `consulta_pld.motivo`, audit (truncado 200) | Auditoria interna. NAO em GET `/onboarding/empresa/{id}` ou `/representantes`. |
| Severidade do hit | `consulta_pld.severidade`, audit | Auditoria interna. |
| `data_inclusao` em lista PLD | `consulta_pld.data_inclusao` | Auditoria interna. |
| Payload bruto do provider | `consulta_pld.payload_provider`, `consulta_cnpj.payload_provider` | Auditoria/compliance via acesso direto ao DB. NUNCA em audit log nem REST. |
| Binarios de documentos | `documento_cadastral.conteudo` | Auditoria/compliance via acesso direto ao DB. NUNCA em REST. |

Respostas REST PJ expoem apenas:
- `representantes[].pld.statusPld` (`PENDENTE`/`LIMPO`/`HIT`).
- `representantes[].pld.dataConsulta`.
- `representantes[].cpfMascarado` (3 primeiros + 2 ultimos digitos).

`ConsultaPldResumoResponse` e o DTO publico — propositalmente minimalista.

## 6. Retencao (LGPD Art. 16 + CMN 4.656/2018)

Periodo minimo: **5 anos** contados da `data_consulta`. Materializado em
`consulta_pld.retencao_ate` no ato da insercao.

FK `consulta_pld.solicitacao_id` referencia `solicitacao_onboarding(id)` **sem
`ON DELETE CASCADE`** — exclusao operacional de uma solicitacao **nao** apaga as
consultas PLD vinculadas. Cliente apagado != trilha PLD apagada.

Purge de retencao expirada vira **rotina dedicada na Sprint 14+** — job que filtra
`retencao_ate <= CURRENT_DATE` e pode delegar para exportacao prevista de longo prazo
(arquivamento frio para conformidade BACEN).

Eventos em `audit_log_seguranca` seguem politica de retencao propria da Sprint 5 (a
definir + revisao juridica). Recomendacao tecnica atual: alinhar com os mesmos 5 anos
minimos.

## 7. Fluxo de excecao / manual (Sprint 14+)

Casos previstos que precisam tratativa fora do happy path:

- **Falso positivo confirmado** — hit em base pode ser homonimo. Sprint 14+ ira oferecer
  um fluxo de backoffice que permite marcar `representante_legal.status_pld = LIMPO`
  manualmente com justificativa auditavel + segundo aprovador, sem reabrir o resultado
  PLD persistido em `consulta_pld` (trilha imutavel mantida).
- **Re-execucao apos correcao** — se o representante legal e atualizado (ex: troca de
  diretoria), o backoffice deve poder re-disparar PLD apenas para o novo representante,
  preservando `consulta_pld` antiga.
- **Sancao expirada** — bases publicas removem hits ao longo do tempo. Job periodico
  (Sprint 14+) re-consulta solicitacoes `REPROVADO_PLD` apos N dias para detectar
  reabilitacao automatica.

**Nao implementado na Sprint 7.** Documento atualizado quando Sprint 14 entrar.

## 8. Checklist para revisao juridica

### Compliance / regulatorio

- [ ] As 4 bases (COAF/OFAC/INTERPOL/MTE) sao suficientes para o nicho SEP? Falta alguma
      base (ex.: PEP, sancoes nacionais especificas)?
- [ ] Bloqueio total em qualquer hit (sem ponderar severidade) e adequado? Ou queremos
      permitir aprovacao com hit `BAIXA` + nota auditavel?
- [ ] Periodo de retencao de 5 anos esta correto para o tipo de operacao SEP? CMN
      4.656/2018 vs Circular BCB 3.978/2020 — pode requerer mais.
- [ ] Politica de re-execucao automatica apos N dias precisa formalizacao por compliance?
- [ ] Tratamento de homonimo precisa de fluxo formal documentado antes da producao?

### LGPD / privacidade

- [ ] DPO aprova o que e exposto na resposta REST (`statusPld` + `dataConsulta` +
      `cpfMascarado`)?
- [ ] DPO aprova o que entra em `audit_log_seguranca` (campos truncados/sanitizados)?
- [ ] Acesso ao payload bruto em `consulta_pld.payload_provider` precisa de controle
      formal (logging de acesso, RBAC dedicado)?
- [ ] Politica de minimizacao: armazenar `data_inclusao` da base PLD esta dentro do
      principio de necessidade?
- [ ] Anonimizacao apos `retencao_ate` versus delecao completa — qual abordagem
      preferida apos os 5 anos?

### Operacional / governance

- [ ] Definir SLA para revisao manual em caso de hit (Sprint 14+).
- [ ] Definir runbook para incidente envolvendo vazamento de dados PLD.
- [ ] Definir politica de notificacao ao tomador/credor quando onboarding cai em
      `REPROVADO_PLD` — qual mensagem, sem expor base/motivo?
- [ ] Trilha auditavel suficiente para auditoria BACEN/COAF? Precisamos de export
      adicional?

### Pos-aprovacao

- [ ] Atualizar este documento removendo o aviso "PENDENTE revisao juridica" e
      registrando data + responsavel pela aprovacao.
- [ ] Linkar versao aprovada na pasta de compliance interna.
- [ ] Comunicar mudancas materiais a equipe de produto + tech.

## 9. Referencias

- [`ONBOARDING.md`](./ONBOARDING.md) — fluxo, endpoints, providers, smoke
- [Spec 007 — Sprint 7](../../docs-SEP/specs/fase-2/007-sprint-7-onboarding-kyb-empresa.md)
- Resolucao CMN nº 4.656/2018 — Sociedades de Emprestimo entre Pessoas
- Lei nº 9.613/1998 — PLD
- Circular BCB 3.978/2020 — Politica de PLD/FT para instituicoes autorizadas
- LGPD (Lei 13.709/2018) — Art. 16 (retencao para obrigacoes regulatorias)
- Celcoin Background Check: <https://developers.celcoin.com.br/docs/background-check>
