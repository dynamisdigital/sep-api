# PR — Sprint 6 (Onboarding KYC Pessoa Fisica)

Artefato exigido pela Task 6.8 (steps `006-sprint-6-steps.md` §6.8.3). Conteudo abaixo
deve ser usado ao abrir o PR manualmente (`gh pr create --base develop ...` ou via UI).

## Titulo sugerido

```
feat(onboarding): Sprint 6 — Onboarding KYC Pessoa Fisica + Celcoin Provider Pattern
```

## Resumo

- Implementa modulo `onboarding` (KYC PF) conforme Resolucao CMN 4.656/2018 Art. 8.
- Integra Celcoin via Provider Pattern (ADR 0004) com OAuth2 + Resilience4j;
  `FakeKycProvider` para dev/test sem credenciais.
- Webhook KYC dedicado em `POST /api/v1/webhooks/celcoin/kyc` com HMAC + idempotencia
  tardia (callback final reenviado nao re-finaliza).

## Escopo tecnico

### Migrations

| Versao | Conteudo |
| ------ | -------- |
| `V7__criar_tabelas_onboarding_kyc.sql` | 3 tabelas (`solicitacao_onboarding`, `documento_cadastral`, `resultado_verificacao`); indice unico parcial `uq_onboarding_cpf_ativo` para CPF em status ativo; extensao do `chk_audit_seguranca_tipo` com 6 eventos `KYC_*`. |
| `V8__cascade_fk_onboarding_usuario.sql` | (revertida — registrada como historico) Adicionava ON DELETE CASCADE em `fk_solicitacao_onboarding_usuario`. |
| `V9__reverter_cascade_fk_onboarding_usuario.sql` | Reverte CASCADE — risco LGPD/regulatorio. Producao nao deleta usuario fisicamente (PRD §16); isolamento de testes feito via `@AfterEach`. |

### Dominio

- `SolicitacaoOnboarding` (agregado raiz) com maquina de estados explicita +
  `validarPodeIniciarVerificacao()` invocado **antes** de qualquer side effect externo
  para evitar verificacao duplicada na Celcoin.
- `DocumentoCadastral` com SHA-256 do conteudo persistido.
- `ResultadoVerificacao` 1:1 com solicitacao; factory `registrar(...)` rejeita status
  nao-final.
- VOs: `Cpf` (record com validacao de digitos verificadores e sequencias repetidas),
  `TipoDocumento`, `StatusOnboarding` (`isFinal()`, `isAtivo()`).
- 4 eventos: `OnboardingIniciadoEvent`, `DocumentoCadastralEnviadoEvent`,
  `VerificacaoKycDisparadaEvent`, `OnboardingFinalizadoEvent`.

### Provider Pattern

- Port `KycProvider` + DTOs `RequisicaoVerificacaoKyc`,
  `RespostaInicioVerificacao`, `ResultadoKycProvider` (sealed: `EmAndamento` |
  `Finalizado(statusFinal, motivo, payloadProvider)`).
- `FakeKycProvider` (`app.kyc.provider=fake`, default em dev) sempre devolve
  `Finalizado(APROVADO)`.
- `CelcoinKycProvider` (`app.kyc.provider=celcoin`) — HTTP real via
  `RestClientFactory.forProvider("celcoin-kyc", baseUrl)`; OAuth2 client-credentials
  com cache + refresh em `CelcoinOAuthTokenProvider`; Resilience4j (instance
  `celcoin-kyc`: retry 3x em 5xx + IOException/ResourceAccessException, circuit
  breaker, timeout 30s).
- `CelcoinKycMapper` (MapStruct) — mapeia `APPROVED → Finalizado(APROVADO)`,
  `REJECTED → Finalizado(REPROVADO)`, `PENDING → Finalizado(PENDENCIA)`,
  `PROCESSING`/desconhecido → `EmAndamento` (safe default — nao finaliza).

### Use cases (4)

| Use case | Resumo |
| -------- | ------ |
| `IniciarOnboardingPessoaUseCase` | Valida CPF (com mapeamento de `IllegalArgumentException` + null/blank guard → `ValidacaoException`), rejeita duplicidade ativa (`CpfComOnboardingAtivoException` → 409). |
| `EnviarDocumentoUseCase` | MIME whitelist (jpeg/png/pdf), tamanho <= 10MB, ownership (ou ADMIN), transiciona via `registrarDocumentoEnviado()`, SHA-256, persiste via `DocumentoStorage`. |
| `IniciarVerificacaoKycUseCase` | Exige >= 1 RG/CNH/PASSAPORTE + 1 SELFIE; Idempotency-Key deterministica `solicitacaoId:revisaoDocumentos` propagada via MDC; guard de status antes da chamada externa. |
| `ConsultarStatusOnboardingUseCase` | Read-only; owner ou ADMIN; retorna `StatusOnboardingView`. |

### REST + OpenAPI

`OnboardingPessoaController` em `/api/v1/onboarding/pessoa`:

| Metodo | Path | Status | Auth |
| ------ | ---- | ------ | ---- |
| POST   | `/`                     | 201 + Location | JWT |
| POST   | `/{id}/documentos`      | 204            | owner ou ADMIN |
| POST   | `/{id}/verificar`       | 202            | owner ou ADMIN |
| GET    | `/{id}`                 | 200            | owner ou ADMIN |

Springdoc completo (Operation + ApiResponses + Schema); `OpenApiConfigTest` validado
para os novos paths + schemas.

### Webhook

`CelcoinKycWebhookController` em `POST /api/v1/webhooks/celcoin/kyc`:

- HMAC obrigatorio via `WebhookSignatureValidator` (Sprint 4); secret em
  `app.webhooks.secrets.celcoin-kyc` (fonte unica — `webhookSecret` removido de
  `CelcoinKycProperties` para evitar duplicidade de config).
- Aceita header `X-Webhook-Signature` (padrao) ou alias `X-Celcoin-Signature`.
- `Idempotency-Key` obrigatorio; idempotencia via outbox `WebhookEventLog`
  (`RegistrarWebhookEventUseCase`).
- `ProcessarCallbackKycUseCase` trata idempotencia tardia: callback final reenviado
  com chave diferente **nao** re-finaliza solicitacao ja com `ResultadoVerificacao`
  ou status final — mesmo status marca `PROCESSADO` sem reescrita; status divergente
  marca `FALHOU` com warn estruturado.

### Audit listener

`OnboardingAuditListener` consome os 4 eventos e grava em `audit_log_seguranca`
(Sprint 5) com `@TransactionalEventListener(AFTER_COMMIT)` +
`@Transactional(propagation = REQUIRES_NEW)` (rollback do publicador nao gera audit
log + audit log nao participa de txn ja commitada). JSON estruturado via
`ObjectMapper.writeValueAsString` (escape correto para valores externos como
`idVerificacaoExterna`). LGPD: detalhes contem apenas identificadores tecnicos +
sha256.

### application.yml

- `app.kyc.provider=${APP_KYC_PROVIDER:fake}`.
- `app.celcoin.kyc.{base-url, client-id, client-secret}`.
- `app.webhooks.secrets.celcoin-kyc` (fonte unica HMAC).
- `resilience4j.{circuitbreaker,retry,timelimiter}.instances.celcoin-kyc`.
- `spring.servlet.multipart.max-file-size=12MB` / `max-request-size=12MB` (transporte
  permite o use case rejeitar 10MB+1 com 400 em vez do parser estourar 413).

## Como validar

```bash
docker compose up -d postgres
./gradlew clean check        # 346 testes verdes; JaCoCo gate 70%; Spotless
./gradlew bootRun            # provider=fake default; suba e siga smoke manual
```

Smoke manual ponta a ponta (login → criar → upload → verificar → simular callback
HMAC → consultar) em [ONBOARDING.md](ONBOARDING.md). WireMock IT separado:

```bash
./gradlew test --tests '*CelcoinKycProviderIT' --tests '*CelcoinOAuthTokenProviderIT'
```

Postman collection atualizada em
`docs-SEP/docs-sep/sep-api.postman_collection.json` (pasta "Onboarding KYC PF
(Sprint 6)" com 7 requests + variaveis `onboardingSolicitacaoId` e `kycWebhookSecret`).

## Riscos / breaking changes

- Nenhum endpoint existente alterado.
- `app.celcoin.kyc.webhook-secret` **removido** — HMAC do webhook agora apenas em
  `app.webhooks.secrets.celcoin-kyc` (env `APP_WEBHOOK_SECRET_CELCOIN_KYC`). Dev/
  homolog que tinham as duas variaveis devem manter apenas a segunda.
- `spring.servlet.multipart.max-file-size=12MB` (limite de dominio segue 10MB
  enforced pelo use case).
- FK `fk_solicitacao_onboarding_usuario` **sem CASCADE** — producao nao deleta
  usuario fisicamente (PRD §16). Eventual delecao fisica passa a falhar com
  `DataIntegrityViolationException` por design (registros KYC sao auditaveis).
- `TipoEventoSeguranca` ganha 6 eventos `KYC_*`; check constraint da V4 e atualizado
  para incluir os novos valores na V7 sem precisar recriar a tabela
  `audit_log_seguranca`.

## Referencia

- Spec: [`docs-SEP/specs/fase-2/006-sprint-6-onboarding-kyc-pessoa.md`](../../docs-SEP/specs/fase-2/006-sprint-6-onboarding-kyc-pessoa.md)
- Steps: [`docs-SEP/steps-fase-2/backend/006-sprint-6-steps.md`](../../docs-SEP/steps-fase-2/backend/006-sprint-6-steps.md)
- Docs do modulo: [`sep-api/docs/ONBOARDING.md`](ONBOARDING.md)
- ADR 0004 — Provider Pattern: [`docs-SEP/adr/0004-provider-pattern-para-integracoes-externas.md`](../../docs-SEP/adr/0004-provider-pattern-para-integracoes-externas.md)
- ADR 0007 — DDD + Hexagonal: [`docs-SEP/adr/0007-ddd-com-hexagonal-ports-and-adapters-por-modulo.md`](../../docs-SEP/adr/0007-ddd-com-hexagonal-ports-and-adapters-por-modulo.md)
- ADR 0008 — WireMock: [`docs-SEP/adr/0008-wiremock-para-testes-integracao-celcoin.md`](../../docs-SEP/adr/0008-wiremock-para-testes-integracao-celcoin.md)

## Trailer do commit / PR

```
🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```
