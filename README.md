# sep-api

Backend Java 21 + Spring Boot 3.5.x da plataforma SEP (Sociedade de Emprestimo entre Pessoas).

> Documentacao consolidada do produto vive no repositorio [`docs-SEP`](../docs-SEP):
> [PRD](../docs-SEP/docs-sep/PRD.md), [CONTEXT](../docs-SEP/docs-sep/CONTEXT.md), [AGENT.md](../docs-SEP/AGENT.md), [ADRs](../docs-SEP/adr/), [specs](../docs-SEP/specs/), [steps fase 1](../docs-SEP/steps-fase-1/backend/), [steps fase 2](../docs-SEP/steps-fase-2/backend/) e [docs especificos da API](../docs-SEP/repos/sep-api/).

## Setup do desenvolvedor

Apos clonar o repositorio:

1. Instalar Java 21 LTS
2. Instalar Docker e Docker Compose
3. **Configurar pre-commit hook**: `git config core.hooksPath .githooks`
4. Rodar `./gradlew build` pela primeira vez

## Code Style

Este projeto usa **Spotless + Palantir Java Format**.

```bash
# Verificar formatacao
./gradlew spotlessCheck

# Aplicar formatacao
./gradlew spotlessApply

# Verificar tudo (build + tests + spotless)
./gradlew check
```

### IDE

- **IntelliJ**: Settings → Editor → Code Style → Java → "Set from..." → Palantir Style
- **VS Code**: extension "Language Support for Java by Red Hat"

## Cobertura de Testes

JaCoCo com target **70% por modulo** (validacao ativada na Sprint 4).

```bash
./gradlew test jacocoTestReport
# Relatorio HTML: build/reports/jacoco/html/index.html
```

### Exclusoes

- `SepApiApplication`, `config/**`, `dto/**`, `*MapperImpl`, `package-info`, excecoes simples.

## Pre-commit Hooks

Este projeto usa Git hooks customizados em `.githooks/`. **Cada dev precisa configurar uma vez:**

```bash
git config core.hooksPath .githooks
```

O hook `pre-commit` roda `./gradlew spotlessCheck` antes de cada commit. Se o codigo estiver desformatado, o commit e bloqueado e voce ve a mensagem com o comando para corrigir.

### Para pular o hook (use com responsabilidade)

```bash
git commit --no-verify
```

## Continuous Integration

PRs para `main`/`develop` e push para `feature/**`, `develop` e `main` rodam a esteira em duas etapas:

1. **Test, Spotless, JaCoCo** — formato de codigo, JUnit 5 com PostgreSQL 16 via service container e verificacao JaCoCo 70%
2. **Build Package** — empacotamento com `bootJar`, executado apenas se a etapa de testes passar

Resultados ficam em **Actions** no GitHub. Relatorios JaCoCo, resultados de teste e o JAR gerado ficam disponiveis como artifacts por 14 dias.

Esta fase ainda nao publica imagem Docker, nao envia para GHCR e nao faz deploy remoto. Deploy e distribuicao ficam para a trilha futura de infraestrutura.

### Branch protection

- `main` exige PR review + CI verde
- Squash merge apenas
- Branches deletadas automaticamente apos merge

## Conventional Commits

Mensagens de commit seguem [Conventional Commits](https://www.conventionalcommits.org/pt-br/v1.0.0/). Ver [CONTRIBUTING.md](CONTRIBUTING.md) para detalhes.

```
feat(usuarios): adicionar criacao publica
fix(auth): rejeitar token com sub vazia
docs(adr): adicionar ADR 0009
chore: atualizar Spring Boot 3.5.1
```

## Stack

- Java 21 LTS, Spring Boot 3.5.x, Gradle 8.10.2 (Wrapper)
- PostgreSQL 16, Hibernate 6, Flyway, Spring Security 6 + JJWT 0.12.x, BCrypt
- MapStruct, RestClient, Resilience4j, Springdoc OpenAPI 2.x, Actuator, Micrometer + Prometheus
- JUnit 5 + AssertJ + Testcontainers, WireMock 3.x
- Spotless + Palantir Java Format, JaCoCo

Detalhes completos: [PRD §11, §18](../docs-SEP/docs-sep/PRD.md).

## Arquitetura

Monolito modular DDD com Hexagonal/Ports & Adapters por modulo. **Provider Pattern obrigatorio** para integracoes externas (Celcoin BaaS).

Modulos previstos: `identity`, `usuarios`, `onboarding`, `credito`, `contratos`, `cobranca`, `escrow`, `backoffice`, `financeiro`, `credores`, `pix`, `shared`.

Detalhes: [ADR 0001](../docs-SEP/adr/0001-monolito-modular-orientado-a-ddd.md), [ADR 0007](../docs-SEP/adr/0007-ddd-com-hexagonal-ports-and-adapters-por-modulo.md).

## Marco regulatorio

SEP opera sob a Resolucao CMN 4.656/2018. Implicacoes desde a Sprint 1: KYC/KYB obrigatorio, segregacao patrimonial via conta escrow, PLD, auditoria reforcada.

## Sprints

- Sprint 0 — Hygiene & Foundation
- Sprint 1 — Fundacao Tecnica (Spring Boot, Postgres, Flyway)
- Sprint 2 — Gestao de Usuarios
- Sprint 3 — Seguranca/Auth JWT
- Sprint 4 — Erros, Documentacao, Testes, Webhook Receiver
- Sprint 5 — Endurecimento de Seguranca (refresh token, MFA, audit log)
- Sprint 6 — Onboarding KYC Pessoa Fisica (modulo `onboarding`, Provider Celcoin, webhook KYC)
- Sprint 7 — Onboarding KYB PJ + PLD (KybProvider sync, BackgroundCheckProvider, 4 bases PLD, webhooks KYB/PLD, auditoria reforcada)

Detalhamento: [docs-SEP/specs/](../docs-SEP/specs/) e [docs-SEP/steps-fase-1/](../docs-SEP/steps-fase-1/), [docs-SEP/steps-fase-2/](../docs-SEP/steps-fase-2/).

## Modulos

- **`identity`** — JWT, refresh token, MFA, lockout, audit log seguranca (Sprint 5).
- **`usuarios`** — cadastro, perfis, ownership.
- **`onboarding`** — KYC PF (Sprint 6) + KYB PJ + PLD orquestrado (Sprint 7) via Celcoin
  Provider Pattern. Ver [ONBOARDING.md](../docs-SEP/repos/sep-api/ONBOARDING.md) (fluxo completo,
  endpoints, webhooks, providers, smoke) e [PLD.md](../docs-SEP/repos/sep-api/PLD.md) (politica PLD,
  retencao LGPD, checklist juridico).
- **`escrow`** — segregacao patrimonial (CMN 4.656/2018), modelado desde Sprint 1.
- **`shared`** — `ApiExceptionHandler`, `EntidadeAuditavel`, `RestClientFactory`,
  Resilience4j, `WebhookEventLog` outbox (Sprint 4).

## Rodar Onboarding (KYC PF + KYB PJ + PLD) localmente

Detalhamento completo em [ONBOARDING.md](../docs-SEP/repos/sep-api/ONBOARDING.md). Politica PLD
detalhada em [PLD.md](../docs-SEP/repos/sep-api/PLD.md). Resumo:

### Providers Fake (default — dev sem credenciais)

```bash
docker compose up -d postgres
./gradlew bootRun
```

Defaults do `application.yml`: `app.kyc.provider=fake`, `app.kyb.provider=fake`,
`app.pld.provider=fake`.

- `FakeKycProvider` — devolve `Finalizado(APROVADO)` ao receber callback simulado.
- `FakeKybProvider` — devolve situacao `ATIVA` + 1 representante deterministico (CPF
  `52998224725`). `marcarCnpjComoSituacao(cnpj, SUSPENSA|...)` em testes força
  reprovacao.
- `FakeBackgroundCheckProvider` — devolve limpo nas 4 bases.
  `marcarDocumentoComoHit(documento)` em testes força hit.

Smoke manual PF e PJ com curl em [ONBOARDING.md](../docs-SEP/repos/sep-api/ONBOARDING.md). Apos KYC/KYB
APROVADO, o `PldOrchestrationListener` dispara PLD automatico — status final consolida em
`APROVADO_FINAL` (limpo) ou `REPROVADO_PLD` (hit).

### Providers Celcoin (sandbox)

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

# PLD (background check)
export APP_PLD_PROVIDER=celcoin
export APP_CELCOIN_PLD_BASE_URL=https://sandbox.openfinance.celcoin.dev/background-check/v1
export APP_CELCOIN_PLD_CLIENT_ID=...
export APP_CELCOIN_PLD_CLIENT_SECRET=...
export APP_WEBHOOK_SECRET_CELCOIN_PLD=...

./gradlew bootRun
```

`CelcoinOAuthTokenProvider` cacheia tokens por `(clientId@baseUrl)` — KYC/KYB/PLD podem
usar credenciais distintas. Resilience4j (`celcoin-kyc`/`celcoin-kyb`/
`celcoin-background-check`): retry 3x em 5xx + IOException, circuit breaker, timeout
30s.

### Profile `local-wiremock` (dev sem credenciais reais)

Cobre o caminho real (HTTP + OAuth + Resilience4j) dos 3 providers sem precisar de
sandbox Celcoin. Stubs `/token`, `/verifications`, `/companies` e `/background-check`
via `__admin/mappings` do WireMock standalone. Passo a passo em
[ONBOARDING.md#profile-local-wiremock](../docs-SEP/repos/sep-api/ONBOARDING.md#profile-local-wiremock).

### Testes WireMock (suite IT)

```bash
./gradlew test --tests '*CelcoinKycProviderIT' \
               --tests '*CelcoinKybProviderIT' \
               --tests '*CelcoinBackgroundCheckProviderIT' \
               --tests '*CelcoinOAuthTokenProviderIT' \
               --tests '*CelcoinOAuthCrossProviderIT'
```

Cobre Bearer OAuth (por providerKey), retry 5xx, propagacao X-Correlation-Id,
preservacao da Idempotency-Key do caller no MDC, cache de token e isolamento de
credenciais entre KYC/KYB/PLD.
