# Teste de fluxo PR — feature → develop → main

Arquivo descartavel para validar o fluxo Git novo.

Criado em 2026-05-06 na branch `feature/test-fluxo-pr` para diagnosticar
incidente recorrente: erro ao sincronizar `develop` local apos PR merge.

## Hipotese

Force-push em `origin/develop` (sync develop = main) reescreve historico
e causa divergencia local. Solucao: nao force-pushar develop; usar merge
commit em PRs `develop → main` para preservar historicos diferentes.

## Plano do teste

1. Push da branch `feature/test-fluxo-pr` para origin
2. Abrir PR para `develop` (squash merge)
3. Apos merge: verificar se `git pull --ff-only` em develop local funciona
4. Repetir com PR `develop → main` (merge commit)
5. Verificar se develop local continua sincronizando

## Como deletar

Apos concluir o teste, este arquivo pode ser removido em commit `chore`
na proxima sprint, ou via PR de limpeza.
