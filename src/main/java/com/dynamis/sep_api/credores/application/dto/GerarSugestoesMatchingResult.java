package com.dynamis.sep_api.credores.application.dto;

/**
 * Resultado do refresh assistido de sugestoes de matching (Sprint 30 Task 30.3): quantidade de
 * sugestoes novas persistidas nesta execucao (pares ja sugeridos/decididos nao contam).
 */
public record GerarSugestoesMatchingResult(int sugestoesNovas) {}
