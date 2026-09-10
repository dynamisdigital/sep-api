package com.dynamis.sep_api.shared.config;

import com.dynamis.sep_api.shared.exception.CatalogoCodigosErro;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato do catalogo de codigos de erro no documento OpenAPI (Sprint 36 Task 36.5).
 *
 * <p>O documento e lido do <b>runtime</b>, em {@code /v3/api-docs}, e nao de um snapshot: snapshot
 * montado a mao prova que alguem escreveu o arquivo, nao que o servico publica aquilo.
 */
@SpringBootTest
@ActiveProfiles("dev")
class CatalogoCodigosErroContratoTest {

    private static final String API_DOCS = "/v3/api-docs";
    private static final String CODIGO = "$.components.schemas.ErrorResponseDto.properties.codigo";
    private static final Pattern CANONICO = Pattern.compile("^[A-Z]{3,4}-[0-9]{3}-[0-9]{3}$");

    @Autowired
    private WebApplicationContext context;

    private String documento() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        return mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /**
     * O enum do OpenAPI e exatamente o catalogo. Junto com o {@code ParticaoDeCodigosErroTest}, que
     * exige catalogo == codigos aptos do codigo-fonte, isto garante que nenhum codigo excluido chega
     * ao contrato — sem lista fixa de excluidos. A amostra fixa que morava aqui foi invalidada a cada
     * Task da Sprint 37 e saiu na 37.7.
     */
    @Test
    void catalogoPublicadoEIgualAFonteUnica() throws Exception {
        List<String> doDocumento = JsonPath.read(documento(), CODIGO + ".enum");

        assertThat(new TreeSet<>(doDocumento))
                .as("o enum do OpenAPI deriva de CatalogoCodigosErro; divergir significa que ha uma"
                        + " segunda lista sendo mantida a mao")
                .isEqualTo(new TreeSet<>(CatalogoCodigosErro.publicados()));
    }

    @Test
    void todoCodigoPublicadoEhCanonicoESemDuplicata() throws Exception {
        List<String> doDocumento = JsonPath.read(documento(), CODIGO + ".enum");

        assertThat(doDocumento).allMatch(codigo -> CANONICO.matcher(codigo).matches());
        assertThat(doDocumento).doesNotHaveDuplicates();
    }

    /**
     * O campo e opcional por contrato. Marca-lo obrigatorio quebraria todo consumidor dos 13
     * handlers que seguem sem taxonomia, e que continuam omitindo a propriedade.
     *
     * <p>Hoje o schema <b>nao tem</b> {@code required} nenhum — medido, nao suposto. A assercao
     * aceita as duas formas de "opcional", ausencia da chave ou presenca sem {@code codigo}, para
     * nao ficar vermelha quando outro campo virar obrigatorio por motivo alheio a esta sprint.
     */
    @Test
    void codigoNaoEhObrigatorioNoSchemaDeErro() throws Exception {
        String documento = documento();

        assertThat(JsonPath.<Object>read(documento, CODIGO)).isNotNull();

        List<String> obrigatorios = JsonPath.using(
                        Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS))
                .parse(documento)
                .read("$.components.schemas.ErrorResponseDto.required");
        assertThat(obrigatorios == null ? List.<String>of() : obrigatorios).doesNotContain("codigo");
    }

    /**
     * Os tres codigos que a F-Sprint 26 consome. Se sairem do catalogo, o lado web perde o caso de
     * uso declarado — e o Gate 36.0 exigiu conferir isso antes de qualquer Task.
     */
    @Test
    void osTresCodigosDeMfaQueDesbloqueiamAFsprint26EstaoPublicados() {
        assertThat(CatalogoCodigosErro.publicados()).contains("MFA-400-002", "MFA-400-003", "MFA-400-004");
    }

    /**
     * Regressao dirigida da licao da Sprint 35 Task 35.7: mexer em {@code components} apagou, la,
     * 21 descriptions e 17 examples em silencio, e depois o {@code securitySchemes} inteiro. Os
     * numeros sao os do documento medido no Gate 36.0, antes desta sprint.
     */
    @Test
    void publicarOCatalogoNaoApagaDescricoesExemplosNemSeguranca() throws Exception {
        String documento = documento();

        assertThat(JsonPath.<String>read(documento, "$.openapi")).startsWith("3.1");
        assertThat(JsonPath.<String>read(documento, "$.components.securitySchemes.bearerAuth.scheme"))
                .isEqualTo("bearer");
        assertThat(JsonPath.<Integer>read(documento, "$.paths.length()")).isEqualTo(98);
        assertThat(contar(documento, "description")).isGreaterThanOrEqualTo(795);
        assertThat(contar(documento, "example")).isGreaterThanOrEqualTo(137);
    }

    /** Varredura recursiva: conta toda ocorrencia do campo em qualquer profundidade do documento. */
    private static int contar(String documento, String campo) {
        return JsonPath.<List<?>>read(documento, "$..%s".formatted(campo)).size();
    }
}
