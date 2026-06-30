# PRD - Product Requirements Document — sep-api

## 1. Visão Geral do Produto
O **sep-api** é concebido como: Backend Java 21 + Spring Boot 3.5.x da plataforma SEP (Sociedade de Emprestimo entre Pessoas).
> Documentacao consolidada do produto vive no repositorio `docs-SEP`:
> PRD, CONTEXT, AGENT.md, ADRs, specs, steps fase 1, steps fase 2 e docs especificos da API. Ele assegura a conformidade operacional de desenvolvimento na stack **Java** com as dependências chave (**Gradle build.gradle, Docker Config**).


## Infográfico Geral

![Infográfico Geral do Projeto](../docs/assets/infograficos/infografico_visao_geral_projeto.png)

## 2. Requisitos Funcionais (RF)
- **`RF-001` (Integração de Fluxos)**: O sistema deve integrar-se nativamente com as ferramentas corporativas de monitoramento e auditoria em tempo real.
- **`RF-002` (Auditoria de Conformidade)**: Deve gerar relatórios e scores de conformidade de forma não bloqueante a partir da varredura lógica.
- **`RF-003` (Interface de Feedback)**: Deve fornecer indicações claras sobre o status das branches e atividades do sistema.
- **`RF-004` (Trilha de Auditoria)**: Deve manter logs históricos consolidados de execuções de comandos e atualizações.

## 3. Requisitos Não-Funcionais (RNF)
- **`RNF-001` (Performance)**: A indexação e análise das informações técnicas deve ocorrer de forma assíncrona sem afetar a interface principal.
- **`RNF-002` (Segurança)**: Acesso restrito a painéis de configuração privilegiados, com mascaramento visual de dados sensíveis.
- **`RNF-003` (Estilo e Design)**: Interface construída sob a identidade visual premium dark-mode do ecossistema Dynamis.

## 4. Roadmap de Épicos Técnicos
1. **Épico 1 (Foundation)**: Modelagem e persistência estruturada de dados locais.
2. **Épico 2 (Core Logic)**: Desenvolvimento de lógica operacional baseada em Java.
3. **Épico 3 (Interface)**: Telas operacionais e widgets reativos sob PySide6/Qt.
4. **Épico 4 (Auditoria)**: Auditoria autônoma de conformidade e geração de documentação.

## 5. Métricas de Sucesso
- Score de integridade técnica mantido acima de **90/100**.
- Tempo de resposta para consultas a dados locais inferior a **200ms**.

---
<sub style="font-size: 14px; color: #95A9D6;"><i>Documento gerado de forma autônoma por IA para o sep-api — Semantic Docs AI.</i></sub>
