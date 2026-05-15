# Onboarding KYC PF + KYB PJ + PLD

Modulo `onboarding` do `sep-api` — implementa verificacao de identidade KYC PF (Sprint 6),
verificacao cadastral KYB PJ (Sprint 7) e consulta PLD pos-aprovacao (Sprint 7), conforme
exigencia legal da Resolucao CMN 4.656/2018 Art. 8 + Lei 9.613/1998 (PLD).

Detalhes especificos do PLD vivem em [`PLD.md`](./PLD.md).

## Visao geral

### Fluxo PF (KYC)

1. Cliente autenticado abre uma `SolicitacaoOnboarding` tipo `PESSOA` informando CPF,
   nome e data de nascimento.
2. Cliente anexa documentos cadastrais (RG/CNH/PASSAPORTE + SELFIE) em multipart.
3. Cliente dispara verificacao no provider externo (Celcoin) — chamada assincrona.
4. Resultado chega via webhook (`POST /api/v1/webhooks/celcoin/kyc`).
5. KYC `APROVADO` -> `PldOrchestrationListener` dispara automaticamente
   `IniciarPldPessoaUseCase`; resultado consolida em `APROVADO_FINAL` ou `REPROVADO_PLD`.

### Fluxo PJ (KYB + PLD)

1. Cliente autenticado abre uma `SolicitacaoOnboarding` tipo `EMPRESA` informando CNPJ,
   razao social e (opcionalmente) nome fantasia, tipo societario e porte.
2. Cliente anexa `CONTRATO_SOCIAL` (ou `CCMEI`) + `COMPROVANTE_ENDERECO` em multipart.
3. Cliente dispara verificacao KYB. `IniciarVerificacaoKybUseCase` chama o
   `KybProvider` **sincronamente** (Celcoin retorna situacao cadastral + representantes
   legais no proprio response).
4. Situacao `ATIVA` -> KYB `APROVADO` -> `PldOrchestrationListener` dispara
   `IniciarPldEmpresaUseCase` (PLD consulta empresa + cada representante nas 4 bases
   obrigatorias). Situacao diferente de `ATIVA` -> KYB `REPROVADO`; PLD nao dispara.
5. PLD limpo em todos os alvos -> `APROVADO_FINAL`. Qualquer hit -> `REPROVADO_PLD`.

ADMIN tem visao plena em ambos os fluxos: pode consultar, anexar e disparar em qualquer
solicitacao (operacao em nome do cliente). Auditoria preserva o dono real da solicitacao
no evento de dominio.

## Maquina de estados consolidada (PF + PJ)

```
INICIADO
   |  registrarDocumentoEnviado()
   v
DOCUMENTOS_RECEBIDOS  <-- (uploads adicionais incrementam revisaoDocumentos)
   |  marcarEmVerificacao(idVerificacaoExterna)
   v
EM_VERIFICACAO
   |  finalizar(statusFinal)  (PF: callback Celcoin KYC | PJ: retorno sync KybProvider)
   v
APROVADO | REPROVADO | PENDENCIA   (pre-PLD)
   |
   |  PldOrchestrationListener (apenas se APROVADO)
   v
APROVADO_FINAL  (PLD limpo em todos os alvos)
   |
REPROVADO_PLD   (hit em pelo menos uma base PLD)
```

Statuses **finais consolidados**: `APROVADO_FINAL`, `REPROVADO`, `REPROVADO_PLD`,
`PENDENCIA`. `APROVADO` e estado **transitorio pos-KYC/KYB pre-PLD** — re-execucao do
PLD a partir de `APROVADO_FINAL`/`REPROVADO_PLD` e idempotente (early-return).

`StatusOnboarding.validarPodeIniciarVerificacao()` enforça que so `DOCUMENTOS_RECEBIDOS`
permite disparar verificacao. `marcarAprovadoFinal()` so transiciona a partir de
`APROVADO`. `reprovarPorPld()` mesmo gate.

`StatusOnboarding.isAtivo()` controla quais statuses ocupam o CPF/CNPJ para o indice
unico parcial `uq_onboarding_documento_ativo` (todos exceto `REPROVADO` e
`REPROVADO_PLD`).

## Endpoints REST

Todos exigem JWT. Erros padronizados via `ErrorResponseDto` (codigos `ONB-400-*`,
`ONB-404-*`, `ONB-409-*`). Documentacao OpenAPI em `/swagger-ui.html` / `/v3/api-docs`.

### PF — base `/api/v1/onboarding/pessoa`

| Metodo | Path                    | Sucesso       | Autorizacao |
| ------ | ----------------------- | ------------- | ----------- |
| POST   | `/`                     | 201 + Location | autenticado |
| POST   | `/{id}/documentos`      | 204            | owner ou ADMIN |
| POST   | `/{id}/verificar`       | 202            | owner ou ADMIN |
| GET    | `/{id}`                 | 200            | owner ou ADMIN |

### PJ — base `/api/v1/onboarding/empresa`

| Metodo | Path                          | Sucesso       | Autorizacao |
| ------ | ----------------------------- | ------------- | ----------- |
| POST   | `/`                           | 201 + Location | autenticado |
| POST   | `/{id}/documentos`            | 204            | owner ou ADMIN |
| POST   | `/{id}/verificar`             | 202            | owner ou ADMIN |
| GET    | `/{id}`                       | 200            | owner ou ADMIN |
| GET    | `/{id}/representantes`        | 200            | owner ou ADMIN |

Documentos PJ aceitos no upload: `CONTRATO_SOCIAL`, `CCMEI`, `COMPROVANTE_ENDERECO`. Tipos
PF (RG/CNH/PASSAPORTE/SELFIE) sao rejeitados com `ONB-400-016`.

CPF de representantes legais e SEMPRE mascarado nas respostas REST (`529******25`); valor
inteiro existe apenas em `representante_legal.cpf` para PLD e fica restrito a auditoria
interna.

## Webhooks Celcoin

Tres receptores dedicados — todos com HMAC SHA-256 hex obrigatorio, idempotencia via
`Idempotency-Key` + outbox `webhook_event_log`, resposta 202 (novo ou duplicado), 400
para headers/body ausentes, 401 para HMAC invalido. Cada provider tem seu proprio
secret e regra de assinatura.

| Endpoint | Secret env | Propriedade | Sprint |
| -------- | ---------- | ----------- | ------ |
| `POST /api/v1/webhooks/celcoin/kyc` | `APP_WEBHOOK_SECRET_CELCOIN_KYC` | `app.webhooks.secrets.celcoin-kyc` | 6 |
| `POST /api/v1/webhooks/celcoin/kyb` | `APP_WEBHOOK_SECRET_CELCOIN_KYB` | `app.webhooks.secrets.celcoin-kyb` | 7 |
| `POST /api/v1/webhooks/celcoin/pld` | `APP_WEBHOOK_SECRET_CELCOIN_PLD` | `app.webhooks.secrets.celcoin-pld` | 7 |

Headers obrigatorios (todos): `Idempotency-Key`, `X-Webhook-Signature` (alias
`X-Celcoin-Signature`). HMAC e SHA-256 hex do body cru calculado com o secret do
respectivo endpoint. **Cada secret e fonte unica** — nao duplicar nas `*Properties` do
provider correspondente.

### Webhook KYC PF (`/celcoin/kyc`)

Body (compativel com `CelcoinKycResultadoResponse`):

```json
{ "verification_id": "ext-celcoin-001", "status": "APPROVED", "reason": null }
```

Status reconhecidos:
- `APPROVED` -> `Finalizado(APROVADO)` -> dispara PLD automatico via
  `PldOrchestrationListener`.
- `REJECTED` -> `Finalizado(REPROVADO)` -> PLD nao dispara.
- `PENDING`  -> `Finalizado(PENDENCIA)` -> PLD nao dispara.
- `PROCESSING` ou desconhecido -> `EmAndamento` (idempotente).

Callbacks tardios (chave diferente, mesmo `verification_id`) sao aceitos como 202 sem
reescrita; status divergente do existente marca evento `FALHOU` no outbox sem alterar o
resultado.

### Webhook KYB PJ (`/celcoin/kyb`)

KYB e **sincrono via provider** (Sprint 7) — o webhook KYB existe apenas para callbacks
tardios fora do happy path sync (provider eventualmente reabrindo verificacao,
correcao manual via Celcoin, etc). O body espelha o response sync; chega tarde, marcado
como duplicado/idempotente.

### Webhook PLD (`/celcoin/pld`)

Body consolidado por solicitacao — payload precisa cobrir TODOS os alvos esperados (PF:
1 PESSOA; PJ: EMPRESA + cada representante) com TODAS as 4 bases obrigatorias por alvo.
Validacao rejeita payload incompleto antes de qualquer transicao de status.

```json
{
  "external_id": "<solicitacaoId>",
  "results": [
    { "target_type": "PESSOA", "document_number": "52998224725",
      "databases": [
        { "database": "COAF", "hit": false },
        { "database": "OFAC", "hit": false },
        { "database": "INTERPOL", "hit": false },
        { "database": "MTE", "hit": false }
      ]
    }
  ]
}
```

Qualquer `hit=true` em qualquer base + qualquer alvo -> `REPROVADO_PLD`. Todos limpos
-> `APROVADO_FINAL`. Hit details (motivo, severidade, dataInclusao) persistem em
`consulta_pld.payload_provider`; NUNCA expostos em respostas REST publicas ou em
`audit_log_seguranca` (LGPD Art. 16 — retencao 5 anos).

## Provider Patterns

Tres provider ports orquestrados — cada um com adapter `fake` (default dev/test) e
`celcoin` (real, OAuth2 client-credentials + Resilience4j: retry 3x em
IOException/HttpServerErrorException, circuit breaker, timeout 30s).

### KycProvider (PF, Sprint 6)

Port: `onboarding.application.port.out.KycProvider`. Async — `iniciarVerificacao` dispara
KYC, resultado chega via webhook. `ResultadoKycProvider` sealed: `EmAndamento` ou
`Finalizado(statusFinal, motivo, payloadProvider)`.

Selecao: `app.kyc.provider` ∈ {`fake` (default), `celcoin`}. Fake retorna sempre
`Finalizado(APROVADO)` ao receber callback simulado.

### KybProvider (PJ, Sprint 7)

Port: `onboarding.application.port.out.KybProvider`. **Sincrono** — `consultarCnpj`
retorna `RespostaKyb` com situacao cadastral + razao social/nome fantasia/CNAE +
representantes legais (CPF + nome + cargo).

Selecao: `app.kyb.provider` ∈ {`fake` (default), `celcoin`}. Fake retorna sempre
situacao `ATIVA` + 1 representante deterministico (CPF `52998224725`); pode ser
configurado via `FakeKybProvider.marcarCnpjComoSituacao(cnpj, SUSPENSA/INAPTA/...)` em
testes para simular reprovacao.

### BackgroundCheckProvider (PLD, Sprint 7)

Port: `onboarding.application.port.out.BackgroundCheckProvider`. Sincrono;
`consultarPessoa`/`consultarEmpresa` consulta as 4 bases obrigatorias
(COAF/OFAC/INTERPOL/MTE) e devolve `RespostaPld(hits, payloadProvider)`. `hits` vazio =
limpo.

Selecao: `app.pld.provider` ∈ {`fake` (default), `celcoin`}. Fake retorna limpo em todas
as bases por padrao; `FakeBackgroundCheckProvider.marcarDocumentoComoHit(documento)`
em testes força hit em todas as bases consultadas para o alvo.

Detalhes regulatorios/exposicao publica em [`PLD.md`](./PLD.md).

## Como rodar localmente

### Provider fake (default)

```bash
docker compose up -d postgres
./gradlew bootRun
```

`app.kyc.provider=fake` esta no `application.yml`. Disparar verificacao sempre devolve
`Finalizado(APROVADO)` quando o callback simulado e enviado.

### Provider Celcoin (sandbox)

```bash
# KYC PF
export APP_KYC_PROVIDER=celcoin
export APP_CELCOIN_KYC_BASE_URL=https://sandbox.openfinance.celcoin.dev/onboarding/v1
export APP_CELCOIN_KYC_CLIENT_ID=...
export APP_CELCOIN_KYC_CLIENT_SECRET=...
export APP_WEBHOOK_SECRET_CELCOIN_KYC=...

# KYB PJ
export APP_KYB_PROVIDER=celcoin
export APP_CELCOIN_KYB_BASE_URL=https://sandbox.openfinance.celcoin.dev/onboarding/v1
export APP_CELCOIN_KYB_CLIENT_ID=...
export APP_CELCOIN_KYB_CLIENT_SECRET=...
export APP_WEBHOOK_SECRET_CELCOIN_KYB=...

# PLD
export APP_PLD_PROVIDER=celcoin
export APP_CELCOIN_PLD_BASE_URL=https://sandbox.openfinance.celcoin.dev/background-check/v1
export APP_CELCOIN_PLD_CLIENT_ID=...
export APP_CELCOIN_PLD_CLIENT_SECRET=...
export APP_WEBHOOK_SECRET_CELCOIN_PLD=...

./gradlew bootRun
```

`CelcoinOAuthTokenProvider` cacheia tokens por `(clientId@baseUrl)` — KYC, KYB e PLD
podem usar credenciais distintas. Refresh 30s antes de expirar. Cada chamada inclui
`Authorization: Bearer <token>` automaticamente. Cada caller prefere o proprio bloco de
credenciais; se ausente, faz fallback para o primeiro provider configurado (validado
por `CelcoinOAuthCrossProviderIT`).

### Profile `local-wiremock` (dev sem credenciais Celcoin)

Cenario: voce quer exercitar o `CelcoinKycProvider` (HTTP real + Resilience4j + OAuth)
sem ter credenciais sandbox. Sobe um WireMock standalone e aponta o `sep-api` para ele.

1. Baixar e rodar WireMock standalone na porta 8089:

```bash
docker run --rm -p 8089:8080 wiremock/wiremock:3.9.2
```

2. Stub minimo para o token + verifications (via REST API do WireMock):

```bash
# Token endpoint
curl -X POST localhost:8089/__admin/mappings -H "Content-Type: application/json" -d '{
  "request": {"method": "POST", "url": "/onboarding/v1/token"},
  "response": {"status": 200, "headers": {"Content-Type":"application/json"},
               "body": "{\"access_token\":\"wm-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}"}
}'

# POST /verifications
curl -X POST localhost:8089/__admin/mappings -H "Content-Type: application/json" -d '{
  "request": {"method": "POST", "url": "/onboarding/v1/verifications"},
  "response": {"status": 200, "headers": {"Content-Type":"application/json"},
               "body": "{\"verification_id\":\"wm-123\",\"status\":\"PROCESSING\"}"}
}'
```

3. Subir o `sep-api` com profile `local-wiremock`:

```bash
export SPRING_PROFILES_ACTIVE=dev,local-wiremock
export APP_KYC_PROVIDER=celcoin
export APP_CELCOIN_KYC_BASE_URL=http://localhost:8089/onboarding/v1
export APP_CELCOIN_KYC_CLIENT_ID=wm-client
export APP_CELCOIN_KYC_CLIENT_SECRET=wm-secret
./gradlew bootRun
```

Resultado: `CelcoinKycProvider` ativo, mas todas chamadas saem para WireMock local. Util
para validar wiring HTTP/headers/MDC/Resilience4j sem dependencia externa. O webhook
KYC ainda e simulado por POST direto (ver "Smoke manual" abaixo) usando o mesmo
`APP_WEBHOOK_SECRET_CELCOIN_KYC` que configurar.

### Testes WireMock (suite de IT)

Os ITs `Celcoin*ProviderIT` e `CelcoinOAuthTokenProviderIT` usam `WireMockExtension`
programaticamente (porta dinamica por classe, sem container). Rodar isolado:

```bash
./gradlew test --tests '*CelcoinKycProviderIT' \
               --tests '*CelcoinKybProviderIT' \
               --tests '*CelcoinBackgroundCheckProviderIT' \
               --tests '*CelcoinOAuthTokenProviderIT' \
               --tests '*CelcoinOAuthCrossProviderIT'
```

Cobertura: Bearer OAuth real (por providerKey), retry 3x em 5xx, propagacao
X-Correlation-Id, preservacao de Idempotency-Key gravada pelo caller no MDC, cache de
token (1 chamada `/token` para N chamadas `accessToken()`), isolamento de credenciais
entre KYC/KYB/PLD.

### Smoke manual PF

```bash
# 1. Login
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"cliente@sep.test","password":"..."}' | jq -r .accessToken)

# 2. Iniciar
ID=$(curl -s -X POST localhost:8080/api/v1/onboarding/pessoa \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"cpf":"52998224725","nomeCompleto":"Joao","dataNascimento":"1990-01-01"}' | jq -r .id)

# 3. Upload
curl -X POST localhost:8080/api/v1/onboarding/pessoa/$ID/documentos \
  -H "Authorization: Bearer $TOKEN" \
  -F "tipo=RG" -F "arquivo=@./rg.jpg;type=image/jpeg"

curl -X POST localhost:8080/api/v1/onboarding/pessoa/$ID/documentos \
  -H "Authorization: Bearer $TOKEN" \
  -F "tipo=SELFIE" -F "arquivo=@./selfie.jpg;type=image/jpeg"

# 4. Disparar
curl -X POST localhost:8080/api/v1/onboarding/pessoa/$ID/verificar \
  -H "Authorization: Bearer $TOKEN"

# 5. Simular webhook (provider=fake) — KYC APROVADO + PLD followup automatico
PAYLOAD='{"verification_id":"fake-'$ID'","status":"APPROVED"}'
SIG=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "dev-kyc-webhook-secret-change-me" -hex | awk '{print $2}')
curl -X POST localhost:8080/api/v1/webhooks/celcoin/kyc \
  -H "Idempotency-Key: idem-$ID" \
  -H "X-Webhook-Signature: $SIG" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD"

# 6. Conferir — esperar APROVADO_FINAL (PLD limpo no fake)
curl -s localhost:8080/api/v1/onboarding/pessoa/$ID \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Smoke manual PJ

```bash
# 1. Login (igual ao PF)
TOKEN=...

# 2. Iniciar PJ
ID=$(curl -s -X POST localhost:8080/api/v1/onboarding/empresa \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"cnpj":"11222333000181","razaoSocial":"ACME LTDA",
       "tipoSocietario":"LTDA","porte":"ME"}' | jq -r .id)

# 3. Upload documentos minimos PJ
curl -X POST localhost:8080/api/v1/onboarding/empresa/$ID/documentos \
  -H "Authorization: Bearer $TOKEN" \
  -F "tipo=CONTRATO_SOCIAL" -F "arquivo=@./contrato.pdf;type=application/pdf"
curl -X POST localhost:8080/api/v1/onboarding/empresa/$ID/documentos \
  -H "Authorization: Bearer $TOKEN" \
  -F "tipo=COMPROVANTE_ENDERECO" -F "arquivo=@./comprovante.pdf;type=application/pdf"

# 4. Disparar — KYB sync + PLD followup automatico no fake
curl -X POST localhost:8080/api/v1/onboarding/empresa/$ID/verificar \
  -H "Authorization: Bearer $TOKEN"

# 5. Conferir — esperar APROVADO_FINAL com 1 representante LIMPO
curl -s localhost:8080/api/v1/onboarding/empresa/$ID \
  -H "Authorization: Bearer $TOKEN" | jq

# 6. Listar representantes (CPF mascarado)
curl -s localhost:8080/api/v1/onboarding/empresa/$ID/representantes \
  -H "Authorization: Bearer $TOKEN" | jq
```

## Testes

| Teste | Foco |
| ----- | ---- |
| `CpfTest` / `CnpjTest` | Validacao dos digitos do VO |
| `SolicitacaoOnboardingTest` | Transicoes consolidadas (PF/PJ + PLD) |
| `*RepositoryTest` | `@DataJpaTest` contra Postgres local |
| `FakeKycProviderTest` / `FakeKybProviderTest` / `FakeBackgroundCheckProviderTest` | Adapters fake |
| `CelcoinKycMapperTest` / `CelcoinKybMapperTest` / `CelcoinPldMapperTest` | Mapeamento Celcoin -> dominio |
| `CelcoinKycProviderIT` / `CelcoinKybProviderIT` / `CelcoinBackgroundCheckProviderIT` | WireMock — OAuth, retry, parsing, headers |
| `CelcoinOAuthTokenProviderIT` / `CelcoinOAuthCrossProviderIT` | Cache OAuth + isolamento por provider |
| `*UseCaseTest` | Mockito puro — use cases KYC/KYB/PLD + idempotencia tardia |
| `OnboardingAuditListenerTest` | Eventos KYC/KYB/PLD -> audit_log_seguranca + sanitizacao |
| `PldOrchestrationListenerTest` | Trigger PLD pos-KYC/KYB APROVADO |
| `CelcoinKycWebhookControllerTest` / `CelcoinKybWebhookControllerTest` / `CelcoinPldWebhookControllerTest` | HMAC + idempotencia + 202 duplicado |
| `OnboardingPessoaControllerTest` / `OnboardingEmpresaControllerTest` | OpenAPI + ownership + ADMIN + tipos PJ |
| `OnboardingPessoaIT` | E2E PF — KYC + PLD followup automatico |
| `OnboardingEmpresaIT` | E2E PJ — KYB sync + PLD followup + hit em representante + SUSPENSA |
| `PldFollowupIT` | E2E PF KYC -> PLD orquestrado (limpo, hit, KYC reprovado) |

Validacao completa:

```bash
./gradlew check
```

JaCoCo gate 70%; Spotless obrigatorio.

## Cuidados LGPD / regulatorios

- **Documentos sao dados sensiveis.** O binario fica em `documento_cadastral.conteudo`
  (BYTEA, 10MB max via check constraint). Storage S3/MinIO entra em Epic 16.
- **NAO logar** binario, CPF/CNPJ completo, nome completo nem payload bruto do provider.
  Logar apenas `solicitacaoId`, `correlationId`, status HTTP, `sha256` do documento,
  `idVerificacaoExterna`. CPF/CNPJ em logs e audit sao mascarados (3 primeiros + 2
  ultimos digitos).
- **Trilha auditavel reforcada**: alem da `EntidadeAuditavel` padrao, eventos KYC/KYB/PLD
  sao gravados em `audit_log_seguranca` (Sprint 5) via `OnboardingAuditListener` com
  `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` (audit nao
  participa de rollback do publicador e usa nova txn dedicada). Tipos cobertos:
  `KYC_*`, `KYB_*`, `PLD_INICIADO`, `PLD_HIT_DETECTADO`, `PLD_LIMPO`, `PLD_FINALIZADO`
  (check constraint V13).
- **Detalhes de hit PLD** (motivo, severidade, base, dataInclusao) sao SANITIZADOS em
  audit: motivo trunca em 200 chars com sentinel `NAO_INFORMADO`; severidade nula vira
  `NAO_INFORMADA`. Dados completos vivem em `consulta_pld` (LGPD Art. 16 — retencao
  minima 5 anos via `retencao_ate`).
- **Payload bruto do provider** vive em `resultado_verificacao.payload_provider`,
  `consulta_cnpj.payload_provider` e `consulta_pld.payload_provider` (JSONB). NUNCA
  replicar em `audit_log_seguranca` — uso e auditoria regulatoria; volume e conteudo
  justificam tabela dedicada.
- **`PldOrchestrationListener` REQUIRES_NEW**: handlers anotados com
  `@Transactional(propagation = REQUIRES_NEW)` — sem isso, o `@Transactional(REQUIRED)`
  dos use cases PLD junta-se a tx fantasma do AFTER_COMMIT e saves nao sao flushados.
  Lesson learned na Task 7.9.
- **CASCADE proibido** em FKs sensitivas: `fk_solicitacao_onboarding_usuario` (V9
  reverteu V8) e `fk_consulta_pld_solicitacao` (LGPD retencao 5 anos). Producao nao
  deleta usuario fisicamente; PLD sobrevive a exclusao operacional. Isolamento de testes
  E2E e feito via `@AfterEach` explicito (limpar consulta_pld antes de solicitacao).

## Referencias

- [PLD.md](./PLD.md) — politica e cuidados PLD em detalhe
- [Spec 006](../../docs-SEP/specs/fase-2/006-sprint-6-onboarding-kyc-pessoa.md) (PF)
- [Spec 007](../../docs-SEP/specs/fase-2/007-sprint-7-onboarding-kyb-empresa.md) (PJ + PLD)
- [Steps 006](../../docs-SEP/steps-fase-2/backend/006-sprint-6-steps.md)
- [Steps 007](../../docs-SEP/steps-fase-2/backend/007-sprint-7-steps.md)
- [ADR 0004 — Provider Pattern](../../docs-SEP/adr/0004-provider-pattern-para-integracoes-externas.md)
- [ADR 0007 — DDD + Hexagonal](../../docs-SEP/adr/0007-ddd-com-hexagonal-ports-and-adapters-por-modulo.md)
- [ADR 0008 — WireMock para Celcoin](../../docs-SEP/adr/0008-wiremock-para-testes-integracao-celcoin.md)
- Celcoin Onboarding: <https://developers.celcoin.com.br/docs/utilizacao-do-onboarding-celcoin>
- Celcoin Webhooks Onboarding: <https://developers.celcoin.com.br/docs/webhooks-onboarding>
- Celcoin Background Check (PLD): <https://developers.celcoin.com.br/docs/background-check>
- Resolucao CMN 4.656/2018 Art. 8 (KYC/KYB obrigatorio)
- Lei 9.613/1998 + Circular BCB 3.978 (PLD)
- LGPD Art. 16 (retencao minima 5 anos para dados regulatorios)
