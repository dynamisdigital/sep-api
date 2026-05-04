# Contribuindo para o Projeto SEP — sep-api

## Antes de comecar

1. Leia o [PRD](../docs-SEP/docs-sep/PRD.md), o [CONTEXT](../docs-SEP/docs-sep/CONTEXT.md) e o [AGENT.md](../docs-SEP/AGENT.md) (vivem no repo `docs-SEP`)
2. Confirme o setup do dev:
   - Java 21 LTS instalado
   - Docker e Docker Compose funcionais
   - Pre-commit hook configurado: `git config core.hooksPath .githooks`
3. Pegue uma task em uma spec (`docs-SEP/specs/fase-1/00X-...md`) ou abra uma issue

## Workflow de desenvolvimento

1. Crie uma branch a partir de `main`:
   ```bash
   git checkout -b feat/<modulo>/<descricao-curta>
   # ou: fix/<modulo>/<descricao-curta>
   ```

2. Implemente seguindo o spec/step correspondente em `docs-SEP/specs/` e `docs-SEP/steps/backend/`
3. Rode localmente:
   ```bash
   ./gradlew spotlessApply  # auto-formatar
   ./gradlew check          # build + test + spotless
   ```
4. Commit com Conventional Commits (ver abaixo)
5. Abra PR usando o template
6. Aguarde 1 review aprovado + CI verde
7. Squash merge (configurado como default)

## Conventional Commits

Mensagens de commit seguem [Conventional Commits 1.0.0](https://www.conventionalcommits.org/pt-br/v1.0.0/).

### Formato

```
<tipo>(<escopo opcional>): <descricao em portugues, modo imperativo>

[body opcional]

[footer opcional]
```

### Tipos aceitos

| Tipo | Quando usar |
|------|-------------|
| `feat` | Nova funcionalidade visivel ao usuario |
| `fix` | Correcao de bug |
| `docs` | Apenas documentacao (PRD, README, ADR, spec, etc.) |
| `style` | Formatacao, sem mudanca de logica (whitespace, missing semi-colons) |
| `refactor` | Refatoracao sem mudanca de comportamento externo |
| `perf` | Melhoria de performance |
| `test` | Adicionar/corrigir testes |
| `build` | Mudancas no build system, dependencias |
| `ci` | Mudancas no GitHub Actions, configs de CI |
| `chore` | Outras tarefas (configs, infra menor, .gitignore) |
| `revert` | Reverter commit anterior |

### Escopo (opcional, mas recomendado)

Use o nome do modulo de dominio quando aplicavel:

- `feat(usuarios): adicionar endpoint de listagem`
- `fix(identity): corrigir validacao de claims JWT`
- `chore(spotless): atualizar para Palantir 2.50.1`
- `docs(prd): clarificar requisito de KYB`
- `test(escrow): adicionar teste de wallet duplicada`

### Exemplos

```
feat(usuarios): adicionar criacao publica de usuario
fix(auth): rejeitar tokens com claim sub vazia
docs(adr): adicionar ADR 0009 sobre observabilidade
chore: atualizar Spring Boot para 3.5.1
refactor(escrow): extrair MovimentacaoFactory
test(identity): cobrir cenario de token expirado
ci: adicionar cache Gradle no workflow
```

### Body e footer (opcionais)

```
feat(credito): integrar OpenFinanceProvider Celcoin

Implementa a primeira chamada para a API de Open Finance
da Celcoin via Finansystech. Adapter fica em
infrastructure.adapter.openfinance.

Closes #42
BREAKING CHANGE: parametro `cpfTomador` agora e obrigatorio
em CreditoApplicationDto.
```

### Breaking changes

Marque com `BREAKING CHANGE:` no footer ou `!` apos o tipo:

```
feat(usuarios)!: trocar formato do id para UUID v6
```

### Lingua

- **tipo**: ingles (`feat`, `fix`, etc.)
- **escopo**: nome do modulo em ingles ou portugues, conforme nome do pacote
- **descricao + body**: portugues (pt-BR), modo imperativo, primeira letra minuscula, sem ponto final

### Validacao automatica (futura)

A validacao de Conventional Commits sera adicionada ao CI futuramente via [commitlint](https://commitlint.js.org/). Por enquanto e responsabilidade do dev.

> **TODO follow-up**: avaliar adicao de commitlint com Husky para enforcar Conventional Commits no pre-commit hook. Decisao pendente: o overhead vale para um time de 3 devs?

## Code Style

Ver [README - Code Style](README.md#code-style).

## Testes

TDD distribuido: cada Sprint entrega testes correspondentes ao escopo. JaCoCo sera ativado na Sprint 4 com target 70%.

```bash
./gradlew test
./gradlew test jacocoTestReport
```

## Pull Requests

Use o template em `.github/PULL_REQUEST_TEMPLATE.md`. Antes de pedir review:

- [ ] CI verde
- [ ] Spec/step de origem citado no PR
- [ ] Testes adicionados se for codigo
- [ ] Cross-refs validados se mudou decisao

## Duvidas

Abra uma issue ou consulte (no repo `docs-SEP`):

- [PRD](../docs-SEP/docs-sep/PRD.md) - visao do produto
- [CONTEXT](../docs-SEP/docs-sep/CONTEXT.md) - historia das decisoes
- [ADRs](../docs-SEP/adr/) - decisoes arquiteturais
- [Specs](../docs-SEP/specs/) - especificacoes por sprint
- [Steps](../docs-SEP/steps/backend/) - detalhamento granular por sprint
- [AGENT.md](../docs-SEP/AGENT.md) - orientacao para agentes IA
