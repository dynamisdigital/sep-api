# Fixtures WireMock — providers externos (Sprint 32 Task 32.6)

Stubs reutilizaveis por capacidade para o smoke local com WireMock **standalone** (profile
`local-wiremock`). Os ITs de adapter (`Celcoin*ProviderIT`, `ClicksignAssinaturaDigitalProviderIT`)
continuam usando stubs inline (cenarios de retry/erro dinamicos ficam mais claros no teste).

> **ATENCAO**: estes contratos sao **skeletons locais da Fase 4** — rotas/campos assumidos, NAO
> capturas certificadas das APIs reais Celcoin/Clicksign. A validacao contra a documentacao e
> credenciais reais e gate da Fase 5 (`INTEGRACOES-PROVIDERS.md` §ativacao).
>
> Nenhum dado real: ids `*-local-*`, token `local-wiremock-token-ficticio`, sem CPF/CNPJ/nome/
> chave Pix/host externo. O guard `WireMockFixturesGuardTest` falha o build se um host/segredo
> real entrar aqui.

## Layout

```
wiremock/
  onboarding/mappings/   # KYC (/verifications), KYB (/companies), PLD (/background-check) + /token
  assinatura/mappings/   # Clicksign (/api/v1/documents, /api/v1/lists)
  pix/mappings/          # /pix/transfers, /pix/charges, /pix/keys + /token
  escrow/mappings/       # /escrow/accounts, /escrow/wallets + /token
```

Cenarios por capacidade: sucesso, erro de negocio (`422`, ativado pelo header
`X-Simular: erro-negocio`) e timeout simulado (`fixedDelayMilliseconds: 35000` em rota/header
dedicado — acima do read-timeout default de 30s).

## Smoke local (uma instancia por capacidade)

```bash
# baixar o standalone uma vez: https://wiremock.org/docs/standalone/java-jar/
java -jar wiremock-standalone.jar --port 9091 --root-dir src/test/resources/wiremock/onboarding &
java -jar wiremock-standalone.jar --port 9092 --root-dir src/test/resources/wiremock/assinatura &
java -jar wiremock-standalone.jar --port 9093 --root-dir src/test/resources/wiremock/pix &
java -jar wiremock-standalone.jar --port 9094 --root-dir src/test/resources/wiremock/escrow &

SPRING_PROFILES_ACTIVE=dev,local-wiremock ./gradlew bootRun
```

O profile `local-wiremock` (`application-local-wiremock.yml`) seleciona os adapters HTTP
explicitamente e aponta as base-urls para as portas acima com credenciais ficticias. Ele e
**opt-in** — dev/test/prod nunca o herdam.
