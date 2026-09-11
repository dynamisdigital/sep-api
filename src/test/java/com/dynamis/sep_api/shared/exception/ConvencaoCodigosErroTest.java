package com.dynamis.sep_api.shared.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate da convencao dos codigos de erro (ADR 0020; Sprint 37 Task 37.6): formato canonico, prefixo
 * registrado em {@link PrefixoCodigoErro}, prefixo usado so pelo modulo dono, numero aposentado fora
 * de uso e codigo de lancamento resolvivel no proprio arquivo.
 *
 * <p>Complementa o {@link ParticaoDeCodigosErroTest} e reusa dele a definicao do que tem forma de
 * codigo. A particao decide o que e <b>publicado</b>; este gate decide o que pode <b>nascer</b>.
 * Codigo novo fora da convencao reprova aqui, com o motivo, antes de chegar ao catalogo.
 */
class ConvencaoCodigosErroTest {

    private static final Path RAIZ_DOS_MODULOS =
            ParticaoDeCodigosErroTest.FONTE.resolve(Path.of("com", "dynamis", "sep_api"));

    /**
     * Numeros canonicos que ja identificaram uma condicao e sairam de uso (ADR 0020 §3: numero
     * retirado nao volta). Os aposentados fora do formato — os semanticos do pix, o
     * {@code OF-400-001}, o {@code PIX-409-IDEMPOTENCIA-CHAVE} — ja reprovam pelo formato.
     */
    private static final Set<String> APOSENTADOS = Set.of(
            "ONB-400-014", // 37.3b: validacao do webhook KYB -> WHK-400-003/004/005
            "ONB-400-015", // 37.3b: validacao do webhook PLD -> WHK-400-003/004/005
            "PIX-400-002", // 37.3b: validacao do webhook Pix -> WHK-400-003/004
            "WHK-400-002"); // 37.3b: validacao do webhook generico -> WHK-400-003/004

    private static final Pattern CONSTANTE_JAVA = Pattern.compile("[A-Z][A-Z0-9_]*");

    /** Mesmo recorte de pontos de lancamento que a particao le: {@code new Tipo(ARG, ...)} e {@code super(ARG, ...)}. */
    private static final Pattern PONTO_DE_LANCAMENTO = Pattern.compile("(?:new\\s+(?:\\w+\\.)*("
            + String.join(
                    "|",
                    Stream.concat(
                                    ParticaoDeCodigosErroTest.SUBTIPOS_SELADOS.stream(),
                                    ParticaoDeCodigosErroTest.ORFAS_COM_HANDLER.stream())
                            .toList())
            + ")|super)\\s*\\(\\s*([A-Za-z_][\\w.]*)\\s*,");

    @Test
    void todoCodigoTemFormatoCanonico() {
        assertThat(ocorrencias())
                .filteredOn(o ->
                        !ParticaoDeCodigosErroTest.CANONICO.matcher(o.codigo()).matches())
                .as("codigo fora de MOD-STATUS-NNN (ADR 0020 §2)")
                .isEmpty();
    }

    @Test
    void todoPrefixoEstaRegistrado() {
        assertThat(ocorrencias())
                .filteredOn(o -> !registrados().contains(o.prefixo()))
                .as("prefixo fora de PrefixoCodigoErro: registre area e modulo dono antes (ADR 0020 §1)")
                .isEmpty();
    }

    @Test
    void cadaPrefixoSoApareceNoModuloDono() {
        assertThat(ocorrencias().stream()
                        .filter(o -> registrados().contains(o.prefixo()))
                        .filter(o ->
                                !PrefixoCodigoErro.valueOf(o.prefixo()).modulo().equals(o.modulo()))
                        .map(o -> {
                            PrefixoCodigoErro prefixo = PrefixoCodigoErro.valueOf(o.prefixo());
                            return o + ": " + prefixo + " (" + prefixo.area() + ") e do modulo " + prefixo.modulo();
                        })
                        .toList())
                .as("prefixo usado fora do modulo dono — o mesmo prefixo em dois modulos e a colisao que"
                        + " a Sprint 37 desfez no CRD")
                .isEmpty();
    }

    @Test
    void todoModuloDoRegistroExiste() {
        for (PrefixoCodigoErro prefixo : PrefixoCodigoErro.values()) {
            assertThat(RAIZ_DOS_MODULOS.resolve(prefixo.modulo()))
                    .as("modulo dono de %s", prefixo)
                    .isDirectory();
        }
    }

    @Test
    void nenhumNumeroAposentadoVoltaAoUso() {
        assertThat(ocorrencias())
                .filteredOn(o -> APOSENTADOS.contains(o.codigo()))
                .as("numero aposentado reutilizado com outro significado (ADR 0020 §3)")
                .isEmpty();
    }

    /**
     * A particao resolve o codigo de um ponto de lancamento pelas constantes do proprio arquivo, e so
     * pelas que ela reconhece: nome com {@code COD}/{@code CODIGO} e valor literal
     * ({@link ParticaoDeCodigosErroTest#CONSTANTE}). Fora disso o ponto fica invisivel e o codigo sai
     * como inalcancavel — nunca publicado, sem nada reprovar. Quatro formas caem nisso:
     *
     * <ul>
     *   <li>constante herdada ({@code super(CODIGO, ...)} num subtipo) — a mutacao mP2 da Task 37.4;
     *   <li>constante qualificada ({@code new ValidacaoException(Outra.CODIGO, ...)});
     *   <li>alias sem literal ({@code CODIGO = Outra.CODIGO});
     *   <li>codigo numa constante sem {@code COD} no nome ({@code ERRO_X = "PIX-400-011"}) — achado do
     *       code review da Task 37.6, provado por mutacao.
     * </ul>
     *
     * <p>Parametro ({@code super(codigo, mensagem)}), literal e constante de mensagem
     * ({@code MOTIVO_SANITIZADO = "Falha ..."}) continuam validos.
     */
    @Test
    void todoCodigoDeLancamentoResolveNoProprioArquivo() {
        List<String> invisiveis = new ArrayList<>();
        for (Path arquivo : javas()) {
            String texto = ler(arquivo);
            boolean ehExcecao = arquivo.getFileName().toString().endsWith("Exception.java");
            Set<String> legiveis = ParticaoDeCodigosErroTest.CONSTANTE
                    .matcher(texto)
                    .results()
                    .map(r -> r.group(1))
                    .collect(Collectors.toSet());
            Matcher ponto = PONTO_DE_LANCAMENTO.matcher(texto);
            while (ponto.find()) {
                boolean ehSuper = ponto.group(1) == null;
                String argumento = ponto.group(2);
                if ((!ehSuper || ehExcecao) && invisivelParaAParticao(argumento, texto, legiveis)) {
                    invisiveis.add(argumento + " em " + RAIZ_DOS_MODULOS.relativize(arquivo) + ":"
                            + linhaDe(texto, ponto.start()));
                }
            }
        }
        assertThat(invisiveis)
                .as("codigo de lancamento que a particao nao consegue ler: declare no proprio arquivo uma"
                        + " constante CODIGO_* com o literal, ou passe o literal")
                .isEmpty();
    }

    private static boolean invisivelParaAParticao(String argumento, String texto, Set<String> legiveis) {
        if (argumento.contains(".")) {
            return true; // qualificada: Outra.CODIGO
        }
        if (!CONSTANTE_JAVA.matcher(argumento).matches()) {
            return false; // parametro: super(codigo, mensagem)
        }
        Matcher declaracao = Pattern.compile("final\\s+String\\s+" + argumento + "\\s*=\\s*([^;]+);")
                .matcher(texto);
        if (!declaracao.find() || !declaracao.group(1).strip().startsWith("\"")) {
            return true; // herdada, ou alias sem literal
        }
        boolean temFormaDeCodigo = ParticaoDeCodigosErroTest.FORMA
                .matcher(declaracao.group(1).strip())
                .matches();
        return temFormaDeCodigo && !legiveis.contains(argumento);
    }

    private record Ocorrencia(String codigo, String modulo, String local) {

        String prefixo() {
            return codigo.substring(0, codigo.indexOf('-'));
        }

        @Override
        public String toString() {
            return codigo + " em " + local;
        }
    }

    private static Set<String> registrados() {
        return Arrays.stream(PrefixoCodigoErro.values()).map(Enum::name).collect(Collectors.toSet());
    }

    private static List<Ocorrencia> ocorrencias() {
        List<Ocorrencia> ocorrencias = new ArrayList<>();
        for (Path arquivo : javas()) {
            String texto = ler(arquivo);
            Path relativo = RAIZ_DOS_MODULOS.relativize(arquivo);
            Matcher literal = ParticaoDeCodigosErroTest.FORMA.matcher(texto);
            while (literal.find()) {
                String antes = texto.substring(Math.max(0, literal.start() - 40), literal.start());
                if (ParticaoDeCodigosErroTest.EXEMPLO_DE_SCHEMA.matcher(antes).find()) {
                    continue;
                }
                ocorrencias.add(new Ocorrencia(
                        literal.group(1),
                        relativo.getName(0).toString(),
                        relativo + ":" + linhaDe(texto, literal.start())));
            }
        }
        assertThat(ocorrencias)
                .as("a varredura nao encontrou codigo nenhum — o caminho de %s mudou?", RAIZ_DOS_MODULOS)
                .isNotEmpty();
        return ocorrencias;
    }

    private static List<Path> javas() {
        try (Stream<Path> arquivos = Files.walk(RAIZ_DOS_MODULOS)) {
            return arquivos.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !ParticaoDeCodigosErroTest.ARTEFATOS_DE_PUBLICACAO.contains(
                            p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("nao foi possivel varrer " + RAIZ_DOS_MODULOS.toAbsolutePath(), e);
        }
    }

    private static String ler(Path arquivo) {
        try {
            return Files.readString(arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException("nao foi possivel ler " + arquivo, e);
        }
    }

    private static int linhaDe(String texto, int posicao) {
        return (int) texto.substring(0, posicao).chars().filter(c -> c == '\n').count() + 1;
    }
}
