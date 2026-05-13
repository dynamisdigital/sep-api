# Onboarding KYC Pessoa Fisica

Modulo `onboarding` do `sep-api` — implementa verificacao de identidade KYC PF conforme
exigencia legal da Resolucao CMN 4.656/2018 Art. 8. Entregue na Sprint 6.

## Visao geral

Fluxo end-to-end:

1. Cliente autenticado abre uma `SolicitacaoOnboarding` informando CPF, nome e data de
   nascimento.
2. Cliente anexa documentos cadastrais (RG/CNH/PASSAPORTE + SELFIE) em multipart.
3. Cliente dispara a verificacao no provider externo (Celcoin) — chamada assincrona.
4. Resultado chega via webhook (`POST /api/v1/webhooks/celcoin/kyc`).
5. Cliente (ou ADMIN) consulta status; quando final, `ResultadoVerificacao` esta
   disponivel.

ADMIN tem visao plena: pode consultar, anexar e disparar em qualquer solicitacao
(operacao em nome do cliente). Auditoria preserva o dono real da solicitacao no evento de
dominio.

## Maquina de estados

```
INICIADO
   |  registrarDocumentoEnviado()
   v
DOCUMENTOS_RECEBIDOS  <-- (uploads adicionais incrementam revisaoDocumentos)
   |  marcarEmVerificacao(idVerificacaoExterna)
   v
EM_VERIFICACAO
   |  finalizar(statusFinal)  (apenas via callback Celcoin)
   v
APROVADO | REPROVADO | PENDENCIA   (status finais)
```

Statuses finais bloqueiam novos documentos e novas chamadas de verificacao
(`SolicitacaoOnboarding.validarPodeIniciarVerificacao()` enforça o invariante antes de
qualquer side effect externo).

`StatusOnboarding.isAtivo()` controla quais statuses ocupam o CPF para o indice unico
parcial `uq_onboarding_cpf_ativo` (todos exceto REPROVADO, que libera o CPF).

## Endpoints REST

Base: `/api/v1/onboarding/pessoa` (todos exigem JWT)

| Metodo | Path                    | Sucesso       | Autorizacao |
| ------ | ----------------------- | ------------- | ----------- |
| POST   | `/`                     | 201 + Location | autenticado |
| POST   | `/{id}/documentos`      | 204            | owner ou ADMIN |
| POST   | `/{id}/verificar`       | 202            | owner ou ADMIN |
| GET    | `/{id}`                 | 200            | owner ou ADMIN |

Erros padronizados via `ErrorResponseDto` (codigos `ONB-400-*`, `ONB-404-001`, `ONB-409-001`).
Documentacao OpenAPI em `/swagger-ui.html` / `/v3/api-docs`.

## Webhook Celcoin

`POST /api/v1/webhooks/celcoin/kyc`

Headers obrigatorios:
- `Idempotency-Key` — chave unica por callback.
- `X-Webhook-Signature` ou alias `X-Celcoin-Signature` — HMAC SHA-256 hex do body.

Secret HMAC: `app.webhooks.secrets.celcoin-kyc` (env var
`APP_WEBHOOK_SECRET_CELCOIN_KYC`). **Fonte unica** — nao duplicar em `CelcoinKycProperties`.

Body esperado (compativel com `CelcoinKycResultadoResponse`):

```json
{ "verification_id": "ext-celcoin-001", "status": "APPROVED", "reason": null }
```

Status reconhecidos:
- `APPROVED` -> `Finalizado(APROVADO)`
- `REJECTED` -> `Finalizado(REPROVADO)`
- `PENDING`  -> `Finalizado(PENDENCIA)`
- `PROCESSING` ou desconhecido -> `EmAndamento` (nao finaliza, idempotente)

Resposta sempre 202 (novo ou duplicado idempotente). 400 para headers/body
ausentes. 401 para HMAC invalido.

Idempotencia: outbox `webhook_event_log` (Sprint 4) marca eventos por
`Idempotency-Key`. Callbacks tardios (chave diferente, mesmo `verification_id`) sao
aceitos como 202 sem reescrita; status divergente do existente marca evento `FALHOU` no
outbox sem alterar o resultado.

## Provider Pattern (KycProvider)

Port: `onboarding.application.port.out.KycProvider`

```java
public interface KycProvider {
    RespostaInicioVerificacao iniciarVerificacao(RequisicaoVerificacaoKyc req, String correlationId);
    ResultadoKycProvider consultarResultado(String idVerificacaoExterna, String correlationId);
}
```

`ResultadoKycProvider` e sealed: `EmAndamento(payloadProvider)` ou
`Finalizado(statusFinal, motivo, payloadProvider)`. `Finalizado` exige status final no
construtor (guard de dominio).

Selecao por `app.kyc.provider`:
- `fake` (default) — `FakeKycProvider`, sempre `Finalizado(APROVADO)`.
- `celcoin` — `CelcoinKycProvider`, HTTP real com OAuth2 client-credentials +
  Resilience4j (`celcoin-kyc`: retry 3x em IOException/HttpServerErrorException, circuit
  breaker, timeout 30s).

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
export APP_KYC_PROVIDER=celcoin
export APP_CELCOIN_KYC_BASE_URL=https://sandbox.openfinance.celcoin.dev/onboarding/v1
export APP_CELCOIN_KYC_CLIENT_ID=...
export APP_CELCOIN_KYC_CLIENT_SECRET=...
export APP_WEBHOOK_SECRET_CELCOIN_KYC=...
./gradlew bootRun
```

`CelcoinOAuthTokenProvider` cacheia o `access_token` (refresh 30s antes de expirar).
Cada chamada KYC inclui `Authorization: Bearer <token>` automaticamente.

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

Os ITs `CelcoinKycProviderIT` e `CelcoinOAuthTokenProviderIT` usam
`WireMockExtension` programaticamente (porta dinamica por classe, sem container). Rodar
isolado:

```bash
./gradlew test --tests '*CelcoinKycProviderIT' --tests '*CelcoinOAuthTokenProviderIT'
```

Cobertura: Bearer OAuth real, retry 3x em 5xx, propagacao X-Correlation-Id,
preservacao de Idempotency-Key gravada pelo caller no MDC, cache de token (1 chamada
`/token` para N chamadas `accessToken()`).

### Smoke manual

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

# 5. Simular webhook (provider=fake)
PAYLOAD='{"verification_id":"fake-'$ID'","status":"APPROVED"}'
SIG=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "dev-kyc-webhook-secret-change-me" -hex | awk '{print $2}')
curl -X POST localhost:8080/api/v1/webhooks/celcoin/kyc \
  -H "Idempotency-Key: idem-$ID" \
  -H "X-Webhook-Signature: $SIG" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD"

# 6. Conferir
curl -s localhost:8080/api/v1/onboarding/pessoa/$ID \
  -H "Authorization: Bearer $TOKEN" | jq
```

## Testes

| Teste | Foco |
| ----- | ---- |
| `CpfTest` | Validacao dos digitos do VO |
| `SolicitacaoOnboardingTest` | Transicoes de estado do agregado |
| `*RepositoryTest` | `@DataJpaTest` contra Postgres local |
| `FakeKycProviderTest` | Adapter fake |
| `CelcoinKycMapperTest` | Mapeamento status Celcoin -> dominio |
| `CelcoinKycProviderIT` | WireMock — Bearer OAuth, retry 5xx, parsing |
| `CelcoinOAuthTokenProviderIT` | Cache OAuth |
| `*UseCaseTest` | Mockito puro, 4 use cases + idempotencia tardia do callback |
| `OnboardingAuditListenerTest` | Eventos KYC -> audit_log_seguranca, escape JSON |
| `CelcoinKycWebhookControllerTest` | HMAC + idempotencia headers |
| `OnboardingPessoaControllerTest` | OpenAPI + ownership + ADMIN |
| `OnboardingPessoaIT` | E2E ponta a ponta (RestAssured + Postgres local) |

Validacao completa:

```bash
./gradlew check
```

JaCoCo gate 70%; Spotless obrigatorio.

## Cuidados LGPD / regulatorios

- **Documentos sao dados sensiveis.** O binario fica em `documento_cadastral.conteudo`
  (BYTEA, 10MB max via check constraint). Storage S3/MinIO entra em Epic 16.
- **NAO logar** binario, CPF completo, nome completo nem payload bruto do provider. Logar
  apenas `solicitacaoId`, `correlationId`, status HTTP, `sha256` do documento,
  `idVerificacaoExterna`.
- **Trilha auditavel reforcada**: alem da `EntidadeAuditavel` padrao, eventos KYC sao
  gravados em `audit_log_seguranca` (Sprint 5) via `OnboardingAuditListener` com
  `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` (audit nao
  participa de rollback do publicador e usa nova txn dedicada).
- **Payload bruto do provider** vive em `resultado_verificacao.payload_provider` (JSONB).
  NUNCA replicar em `audit_log_seguranca` — uso e auditoria regulatoria; volume e
  conteudo justificam tabela dedicada.
- **CASCADE proibido** em `fk_solicitacao_onboarding_usuario` (V9 reverteu V8). Producao
  nao deleta usuario fisicamente (PRD §16). Isolamento de testes E2E e feito via
  `@AfterEach` explicito.

## Referencias

- [Spec 006](../../docs-SEP/specs/fase-2/006-sprint-6-onboarding-kyc-pessoa.md)
- [Steps 006](../../docs-SEP/steps-fase-2/backend/006-sprint-6-steps.md)
- [ADR 0004 — Provider Pattern](../../docs-SEP/adr/0004-provider-pattern-para-integracoes-externas.md)
- [ADR 0007 — DDD + Hexagonal](../../docs-SEP/adr/0007-ddd-com-hexagonal-ports-and-adapters-por-modulo.md)
- [ADR 0008 — WireMock para Celcoin](../../docs-SEP/adr/0008-wiremock-para-testes-integracao-celcoin.md)
- Celcoin Onboarding: <https://developers.celcoin.com.br/docs/utilizacao-do-onboarding-celcoin>
- Celcoin Webhooks Onboarding: <https://developers.celcoin.com.br/docs/webhooks-onboarding>
- Resolucao CMN 4.656/2018 Art. 8 (KYC obrigatorio)
