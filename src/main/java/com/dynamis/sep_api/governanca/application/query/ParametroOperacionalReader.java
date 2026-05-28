package com.dynamis.sep_api.governanca.application.query;

import java.math.BigDecimal;

/**
 * Porta de leitura de parametros operacionais governados, oferecida pelo modulo {@code governanca}
 * aos modulos consumidores (Sprint 18). Cada acessor recebe um {@code default} usado quando o
 * parametro nao existe ou esta inativo — permitindo adocao incremental sem quebrar regras que ainda
 * leem properties.
 */
public interface ParametroOperacionalReader {

    int lerInteiro(String chave, int valorPadrao);

    BigDecimal lerDecimal(String chave, BigDecimal valorPadrao);

    boolean lerBooleano(String chave, boolean valorPadrao);

    String lerTexto(String chave, String valorPadrao);
}
