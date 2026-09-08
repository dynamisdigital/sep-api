package com.dynamis.sep_api.shared.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova da particao entre codigos publicados e excluidos (Sprint 36 Task 36.7, criterio de aceite 6).
 *
 * <p><b>Por que isto e um teste, e nao um script.</b> O catalogo e uma lista literal — 46 dos 133
 * codigos existem so como literal inline e 19 dos publicados sao {@code private}, entao nao ha como
 * o compilador liga-lo ao codigo-fonte (ver {@link CatalogoCodigosErro}). A unica garantia possivel
 * e uma verificacao executavel, e uma verificacao que so roda quando alguem lembra de invocar nao
 * guarda nada. Aqui ela roda em todo {@code ./gradlew build}.
 *
 * <p>Varre {@code src/main/java} por duas vias — literal com forma de codigo e constante cujo nome
 * indica codigo — e reclassifica tudo do zero pelos tres criterios do perimetro. Nao le nenhum
 * numero registrado em documento: se a spec e a medicao divergirem, quem manda e a medicao.
 */
class ParticaoDeCodigosErroTest {

    private static final Path FONTE = Path.of("src", "main", "java");

    private static final Pattern FORMA = Pattern.compile("\"([A-Z]{2,5}-[0-9]{3}-[A-Z0-9_-]+)\"");
    private static final Pattern CANONICO = Pattern.compile("^[A-Z]{3,4}-[0-9]{3}-[0-9]{3}$");
    private static final Pattern CONSTANTE =
            Pattern.compile("(?:public|private|protected)?\\s*(?:static\\s+)?final\\s+String\\s+"
                    + "([A-Za-z_]*(?:COD|CODIGO)[A-Za-z_]*)\\s*=\\s*\"([^\"]+)\"");
    /** {@code example = "AUTH-423-001"} num {@code @Schema} documenta o campo; nao define o codigo. */
    private static final Pattern EXEMPLO_DE_SCHEMA = Pattern.compile("example\\s*=\\s*$");

    private static final Set<String> SUBTIPOS_SELADOS = Set.of(
            "ValidacaoException",
            "RecursoNaoEncontradoException",
            "ConflitoException",
            "AcessoNegadoException",
            "OperacaoNaoProcessavelException");

    /** Fora da hierarquia selada, mas com {@code @ExceptionHandler} dedicado que le a constante. */
    private static final Set<String> ORFAS_COM_HANDLER = Set.of(
            "ContaBloqueadaException", "LimiteReprocessoExcedidoException", "TipoReprocessoNaoSuportadoException");

    /** O catalogo publica os codigos; contabiliza-lo como dono faria dele dono de todos eles. */
    private static final Set<String> ARTEFATOS_DE_PUBLICACAO = Set.of("CatalogoCodigosErro.java");

    @Test
    void oCatalogoContemExatamenteOsCodigosAptosDoCodigoFonte() {
        Inventario inventario = varrer();

        assertThat(inventario.aptos())
                .as("catalogo e codigo-fonte divergiram: um codigo apto ficou de fora do contrato, ou"
                        + " um codigo do contrato deixou de ser apto")
                .isEqualTo(new TreeSet<>(CatalogoCodigosErro.publicados()));
    }

    @Test
    void aParticaoEhCompletaEDisjunta() {
        Inventario inventario = varrer();

        Set<String> publicados = inventario.aptos();
        Set<String> excluidos = new TreeSet<>(inventario.donos.keySet());
        excluidos.removeAll(publicados);

        assertThat(publicados).doesNotContainAnyElementsOf(excluidos);
        assertThat(publicados.size() + excluidos.size()).isEqualTo(inventario.donos.size());
    }

    @Test
    void todoExcluidoTemMotivoAtribuivel() {
        Inventario inventario = varrer();

        for (String codigo : inventario.donos.keySet()) {
            if (inventario.aptos().contains(codigo)) {
                continue;
            }
            assertThat(motivoDe(codigo, inventario))
                    .as("excluido sem motivo atribuivel: %s", codigo)
                    .isIn("formato", "colisao", "inalcancavel");
        }
    }

    /** Ordem dos criterios: e a mesma que o documento operacional usa como motivo primario. */
    private static String motivoDe(String codigo, Inventario inventario) {
        if (!CANONICO.matcher(codigo).matches()) {
            return "formato";
        }
        if (inventario.donos.get(codigo).size() > 1) {
            return "colisao";
        }
        return "inalcancavel";
    }

    private record Inventario(Map<String, Set<String>> donos, Set<String> alcancaveis) {

        Set<String> aptos() {
            Set<String> aptos = new TreeSet<>();
            for (Map.Entry<String, Set<String>> entrada : donos.entrySet()) {
                String codigo = entrada.getKey();
                if (CANONICO.matcher(codigo).matches()
                        && entrada.getValue().size() == 1
                        && alcancaveis.contains(codigo)) {
                    aptos.add(codigo);
                }
            }
            return aptos;
        }
    }

    private static Inventario varrer() {
        Map<String, Set<String>> donos = new TreeMap<>();
        Set<String> alcancaveis = new HashSet<>();

        try (Stream<Path> arquivos = Files.walk(FONTE)) {
            List<Path> javas = arquivos.filter(p -> p.toString().endsWith(".java"))
                    .filter(p ->
                            !ARTEFATOS_DE_PUBLICACAO.contains(p.getFileName().toString()))
                    .toList();
            for (Path arquivo : javas) {
                processar(arquivo, ler(arquivo), donos, alcancaveis);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("nao foi possivel varrer " + FONTE.toAbsolutePath(), e);
        }

        assertThat(donos)
                .as("a varredura nao encontrou codigo nenhum — o caminho de %s mudou?", FONTE)
                .isNotEmpty();
        return new Inventario(donos, alcancaveis);
    }

    private static String ler(Path arquivo) {
        try {
            return Files.readString(arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException("nao foi possivel ler " + arquivo, e);
        }
    }

    private static void processar(Path arquivo, String texto, Map<String, Set<String>> donos, Set<String> alcancaveis) {
        String classe = arquivo.getFileName().toString().replace(".java", "");

        Map<String, String> constantes = new HashMap<>();
        Matcher constante = CONSTANTE.matcher(texto);
        while (constante.find()) {
            String valor = constante.group(2);
            if (FORMA.matcher("\"" + valor + "\"").matches()) {
                constantes.put(constante.group(1), valor);
                donos.computeIfAbsent(valor, ignored -> new TreeSet<>()).add(classe);
            }
        }

        Matcher literal = FORMA.matcher(texto);
        while (literal.find()) {
            if (ehExemploDeSchema(texto, literal.start())) {
                continue;
            }
            donos.computeIfAbsent(literal.group(1), ignored -> new TreeSet<>()).add(classe);
        }

        // A excecao orfa nao passa por construtor de subtipo selado; o handler le a constante dela.
        if (ORFAS_COM_HANDLER.contains(classe)) {
            alcancaveis.addAll(constantes.values());
        }
        marcarAlcancaveis(texto, constantes, alcancaveis);
    }

    private static boolean ehExemploDeSchema(String texto, int posicao) {
        return EXEMPLO_DE_SCHEMA
                .matcher(texto.substring(Math.max(0, posicao - 40), posicao))
                .find();
    }

    /**
     * Alcance = existir construtor de um tipo que chegue ao {@code ApiExceptionHandler} recebendo o
     * codigo como primeiro argumento. Le o texto inteiro, e nao linha a linha: a maioria dos
     * {@code throw new ...(CODIGO, "mensagem")} do repo ocupa tres linhas.
     */
    private static void marcarAlcancaveis(String texto, Map<String, String> constantes, Set<String> alcancaveis) {
        for (String tipo : Stream.concat(SUBTIPOS_SELADOS.stream(), ORFAS_COM_HANDLER.stream())
                .toList()) {
            Matcher chamada = Pattern.compile("new\\s+(?:\\w+\\.)*" + tipo + "\\s*\\(\\s*(\"[^\"]+\"|[A-Za-z_]+)")
                    .matcher(texto);
            while (chamada.find()) {
                String bruto = chamada.group(1);
                String codigo = bruto.startsWith("\"") ? bruto.substring(1, bruto.length() - 1) : constantes.get(bruto);
                if (codigo != null) {
                    alcancaveis.add(codigo);
                }
            }
        }
        // `super(CODIGO, mensagem)` numa excecao que herda de subtipo selado
        Matcher superChamada =
                Pattern.compile("super\\s*\\(\\s*(\"[^\"]+\"|[A-Za-z_]+)").matcher(texto);
        while (superChamada.find()) {
            String bruto = superChamada.group(1);
            String codigo = bruto.startsWith("\"") ? bruto.substring(1, bruto.length() - 1) : constantes.get(bruto);
            if (codigo != null && FORMA.matcher("\"" + codigo + "\"").matches()) {
                alcancaveis.add(codigo);
            }
        }
    }
}
