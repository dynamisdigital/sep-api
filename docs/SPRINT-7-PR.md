# PR — Sprint 7 (Onboarding KYB Empresa + PLD)

Artefato exigido pela Task 7.10 (steps `007-sprint-7-steps.md` §7.10.6). Conteudo abaixo
deve ser usado ao abrir o PR manualmente (`gh pr create --base develop ...` ou via UI).

## Titulo sugerido

```
feat(onboarding): Sprint 7 — Onboarding KYB PJ + PLD + auditoria reforcada
```

## Resumo

- Implementa **onboarding KYB de Pessoa Juridica** (CNPJ, representantes legais,
  situacao cadastral) conforme Resolucao CMN 4.656/2018 Art. 8.
- Integra **`BackgroundCheckProvider` (PLD)** consultando as 4 bases obrigatorias
  (COAF, OFAC, INTERPOL, MTE) por alvo — empresa + cada representante (PJ) ou pessoa
  (PF). `PldOrchestrationListener` dispara PLD automatico apos KYC/KYB `APROVADO`.
- Adiciona `KybProvider` sincrono e `BackgroundCheckProvider` async-friendly, ambos via
  Provider Pattern (ADR 0004) com OAuth2 isolado por providerKey + Resilience4j.
- 5 endpoints REST PJ (`/api/v1/onboarding/empresa/...`) + webhooks dedicados KYB e
  PLD com HMAC e idempotencia.
- Trilha auditavel ampliada com 7 novos tipos `KYB_*`/`PLD_*` em
  `audit_log_seguranca` — detalhes sanitizados (CPF/CNPJ mascarado, motivo truncado,
  severidade nula -> sentinel).

## Escopo tecnico

### Migrations

| Versao | Conteudo |
| ------ | -------- |
| `V10__alterar_solicitacao_onboarding_tipo.sql` | Generaliza `SolicitacaoOnboarding` pra PF/PJ: adiciona `tipo` (`PESSOA`/`EMPRESA`), `documento` (14 chars normalizados), torna `cpf`/`data_nascimento` nullable. Migra dados existentes preservando CPF. Status `APROVADO_FINAL`/`REPROVADO_PLD` adicionados ao check constraint. |
| `V11__criar_tabelas_kyb_pld.sql` | 4 tabelas: `kyb_empresa` (1:1 com solicitacao), `consulta_cnpj` (resposta do KybProvider), `representante_legal` (N:1 com KYB, com `status_pld`), `consulta_pld` (1 por base × alvo, com `retencao_ate` = data + 5 anos para LGPD). FK `consulta_pld.solicitacao_id` SEM `ON DELETE CASCADE` (retencao). |
| `V12__ampliar_tipos_documento_cadastral_pj.sql` | Adiciona `CONTRATO_SOCIAL`, `CCMEI`, `COMPROVANTE_ENDERECO` ao `chk_documento_cadastral_tipo`. |
| `V13__ampliar_audit_seguranca_tipo_kyb_pld.sql` | Adiciona 7 tipos ao `chk_audit_seguranca_tipo`: `KYB_INICIADO`, `KYB_FINALIZADO_APROVADO`, `KYB_FINALIZADO_REPROVADO`, `PLD_INICIADO`, `PLD_HIT_DETECTADO`, `PLD_LIMPO`, `PLD_FINALIZADO`. |

### Dominio

- `SolicitacaoOnboarding` consolidada: factories `criarPessoa(...)` e `criarEmpresa(...)`;
  `marcarAprovadoFinal()` e `reprovarPorPld()` enforce gates pos-PLD.
- `KybEmpresa` (1:1 com solicitacao tipo EMPRESA), `ConsultaCNPJ`, `RepresentanteLegal`
  (com `statusPld` consolidado), `ConsultaPld` (1 por base × alvo, retencao 5 anos).
- VOs: `Cnpj` (record com validacao DV1+DV2 + sequencias repetidas), `AlvoPld`,
  `BasePld`, `SituacaoCadastral`, `SeveridadePld`, `StatusPldRepresentante`,
  `TipoSocietario`, `PorteEmpresa`, `TipoSolicitante`. `TipoDocumento` ganhou 3 valores PJ.
- 4 eventos novos: `KybIniciadoEvent`, `KybFinalizadoEvent`, `PldIniciadoEvent`,
  `PldHitDetectadoEvent` (com `motivo` para auditoria), `PldLimpoEvent`,
  `PldFinalizadoEvent`. Status finais consolidados: `APROVADO_FINAL`, `REPROVADO_PLD`.

### Provider Pattern

- **`KybProvider`** (sincrono) + `RequisicaoKyb` + `RespostaKyb` + `RepresentanteLegalProviderDto`.
  - `FakeKybProvider` (default) — situacao `ATIVA` + 1 representante deterministico;
    `marcarCnpjComoSituacao(cnpj, ...)` permite simular reprovacao em testes.
  - `CelcoinKybProvider` — HTTP real via `RestClient` + `CelcoinOAuthTokenProvider`
    (cache por `providerKey`) + Resilience4j (`celcoin-kyb`: retry 3x, circuit breaker,
    timeout 30s).
- **`BackgroundCheckProvider`** (PLD) + `RequisicaoPld.comBasesObrigatorias(...)` +
  `RespostaPld(hits, payloadProvider)` + `HitPld`.
  - `FakeBackgroundCheckProvider` (default) — limpo em todas as bases;
    `marcarDocumentoComoHit(documento)` força hit em todas as bases consultadas.
  - `CelcoinBackgroundCheckProvider` — HTTP real, mesma stack Resilience4j
    (`celcoin-background-check`).
- `CelcoinOAuthTokenProvider` cacheia tokens por `(clientId@baseUrl)` — KYC/KYB/PLD
  podem ter credenciais independentes; fallback documentado em
  `CelcoinOAuthCrossProviderIT`.

### Endpoints REST PJ (base `/api/v1/onboarding/empresa`)

| Metodo | Path                    | Sucesso | Autorizacao |
| ------ | ----------------------- | ------- | ----------- |
| POST   | `/`                     | 201     | autenticado |
| POST   | `/{id}/documentos`      | 204     | owner ou ADMIN; tipo restrito a `CONTRATO_SOCIAL`/`CCMEI`/`COMPROVANTE_ENDERECO` (PF rejeitado com `ONB-400-016`) |
| POST   | `/{id}/verificar`       | 202     | owner ou ADMIN; KYB sync + PLD followup automatico |
| GET    | `/{id}`                 | 200     | owner ou ADMIN; status consolidado + KYB cadastrais + representantes + resultado |
| GET    | `/{id}/representantes`  | 200     | owner ou ADMIN; CPF SEMPRE mascarado |

DTOs: `IniciarOnboardingEmpresaRequest`, `EmpresaResponse`,
`StatusOnboardingEmpresaResponse`, `RepresentanteLegalResponse`,
`ConsultaPldResumoResponse`. OpenAPI completa em `/v3/api-docs` (Tag `onboarding` +
schemas + 200/201/202/204/400/401/403/404/409 com `ErrorResponseDto`).

### Webhooks (HMAC + idempotencia + outbox)

- `POST /api/v1/webhooks/celcoin/kyb` — receptor dedicado para callbacks KYB tardios
  (KYB primario e sync); HMAC com `APP_WEBHOOK_SECRET_CELCOIN_KYB`.
- `POST /api/v1/webhooks/celcoin/pld` — receptor consolidado PLD; valida cobertura de
  todos os alvos × 4 bases obrigatorias antes de transicionar status.

### Orquestracao + auditoria

- `PldOrchestrationListener` (`@TransactionalEventListener(AFTER_COMMIT)` +
  `@Transactional(REQUIRES_NEW)`) — dispara `IniciarPldPessoa/EmpresaUseCase` apos KYC/
  KYB `APROVADO`. REQUIRES_NEW e critico — sem ele, saves do PLD se perdem na tx
  fantasma do AFTER_COMMIT (regressao detectada e fixada em Task 7.9).
- `OnboardingAuditListener` estendido com 7 handlers para os tipos
  `KYB_*`/`PLD_*`. Detalhes sanitizados (CPF/CNPJ mascarado, motivo trunca em 200
  chars, severidade nula -> `NAO_INFORMADA`). Payload bruto e dados completos NUNCA
  gravados em `audit_log_seguranca`.

### Documentacao

- `docs/ONBOARDING.md` atualizado com fluxo PJ, maquina de estados consolidada (PF+PJ),
  endpoints PJ, webhooks KYB/PLD, providers, smoke manual PJ.
- `docs/PLD.md` criado — bases, criterio de bloqueio, retencao LGPD, o que NUNCA
  exposto publicamente, fluxo excecao (Sprint 14+), checklist para revisao juridica
  (PENDENTE).
- README atualizado com env vars KYB/PLD sandbox e WireMock IT.

## Como validar

```bash
# Build + suite completa + JaCoCo verification
cd <sep-api-root>
docker compose up -d postgres
./gradlew clean check

# Testes especificos Sprint 7
./gradlew test --tests '*OnboardingEmpresaIT' \
               --tests '*PldFollowupIT' \
               --tests '*Iniciar*KybUseCaseTest' \
               --tests '*IniciarPld*UseCaseTest' \
               --tests '*ProcessarCallbackKybUseCaseTest' \
               --tests '*ProcessarCallbackPldUseCaseTest' \
               --tests '*OnboardingEmpresaControllerTest' \
               --tests '*Celcoin*WebhookControllerTest' \
               --tests '*PldOrchestrationListenerTest'

# WireMock IT (providers Celcoin)
./gradlew test --tests '*CelcoinKybProviderIT' \
               --tests '*CelcoinBackgroundCheckProviderIT' \
               --tests '*CelcoinOAuthCrossProviderIT'
```

Smoke REST manual em `docs/ONBOARDING.md#smoke-manual-pj`.

## Riscos / breaking changes

- **`SolicitacaoOnboarding` schema migrado** (`V10`) — dados PF existentes preservam
  CPF em ambas as colunas (`cpf` legado + `documento` novo); reverter exige migration
  reversa (nao prevista).
- **Contratos Celcoin sandbox podem divergir** dos stubs WireMock — durante hardening
  vs ambiente real, ajustes em mappers KYB/PLD sao esperados.
- **PLD bloqueia onboarding em qualquer hit** — politica regulatoria PENDENTE
  revisao juridica (ver `docs/PLD.md` §8). Producao nao deve seguir sem aprovacao
  formal de compliance/DPO.
- **`PldOrchestrationListener` AFTER_COMMIT/REQUIRES_NEW** — qualquer regressao na
  propagacao quebra a transicao final silenciosamente. Cobertura via E2E em
  `OnboardingEmpresaIT` e `PldFollowupIT`.
- **`fk_consulta_pld_solicitacao` sem CASCADE** — testes/cleanup precisam deletar
  `consulta_pld` antes de `solicitacao_onboarding`.

## Referencia

- Spec: [`docs-SEP/specs/fase-2/007-sprint-7-onboarding-kyb-empresa.md`](../../docs-SEP/specs/fase-2/007-sprint-7-onboarding-kyb-empresa.md)
- Steps: [`docs-SEP/steps-fase-2/backend/007-sprint-7-steps.md`](../../docs-SEP/steps-fase-2/backend/007-sprint-7-steps.md)
- ADR 0004 — Provider Pattern
- ADR 0008 — WireMock para Celcoin
- Politica PLD detalhada: [`docs/PLD.md`](./PLD.md)
- Resolucao CMN 4.656/2018 Art. 8; Lei 9.613/1998; LGPD Art. 16
