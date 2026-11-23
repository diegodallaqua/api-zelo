package com.zelo.compartilhado.erro;

import com.zelo.compartilhado.erro.ArquivoInvalidoException.Motivo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cada exceção vira o status certo, com código estável e mensagem em português. */
class ApiExceptionHandlerTest {

    enum Cor { VERDE, CORAL }

    record Entrada(@NotBlank(message = "O nome é obrigatório.") String nome) {
    }

    @RestController
    static class ControladorDeTeste {

        @GetMapping("/erro/{tipo}")
        void lancar(@org.springframework.web.bind.annotation.PathVariable String tipo) {
            switch (tipo) {
                case "400" -> throw new RequisicaoInvalidaException("X_400", "inválido");
                case "401" -> throw new NaoAutenticadoException("X_401", "sem login");
                case "403" -> throw new AcessoNegadoException("X_403", "proibido");
                case "404" -> throw new RecursoNaoEncontradoException("X_404", "não existe");
                case "409" -> throw new ConflitoException("X_409", "conflito");
                case "413" -> throw new ArquivoInvalidoException(Motivo.TAMANHO_EXCEDIDO, "grande");
                case "415" -> throw new ArquivoInvalidoException(Motivo.FORMATO_NAO_SUPORTADO, "formato");
                case "422" -> throw new RegraDeNegocioException("X_422", "regra");
                case "429" -> throw new LimiteExcedidoException("X_429", "limite");
                case "503" -> throw new ServicoIndisponivelException("X_503", "fora");
                default -> throw new IllegalStateException("detalhe interno que não deve vazar");
            }
        }

        @PostMapping("/corpo")
        void corpo(@Valid @RequestBody Entrada entrada) {
        }

        @GetMapping("/parametro")
        void parametro(@RequestParam UUID id, @RequestParam(required = false) Cor cor) {
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ControladorDeTeste())
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validador)
                .build();
    }

    @Test
    void mapeiaExcecoesDeNegocio() throws Exception {
        for (String codigo : new String[]{"400", "401", "403", "404", "409", "413", "415", "422", "429", "503"}) {
            mvc.perform(get("/erro/" + codigo))
                    .andExpect(status().is(Integer.parseInt(codigo)))
                    .andExpect(jsonPath("$.status").value(Integer.parseInt(codigo)))
                    .andExpect(jsonPath("$.codigo").isNotEmpty())
                    .andExpect(jsonPath("$.detail").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
    }

    @Test
    void naoAutenticadoTrazCabecalhoBearer() throws Exception {
        mvc.perform(get("/erro/401")).andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void erroInesperadoNaoVazaDetalhes() throws Exception {
        mvc.perform(get("/erro/qualquer"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente mais tarde."));
    }

    @Test
    void validacaoListaOsCampos() throws Exception {
        mvc.perform(post("/corpo").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erros[0].campo").value("nome"))
                .andExpect(jsonPath("$.erros[0].mensagem").value("O nome é obrigatório."));
    }

    @Test
    void jsonMalFormado() throws Exception {
        mvc.perform(post("/corpo").contentType(MediaType.APPLICATION_JSON).content("{nome"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));
    }

    @Test
    void parametroComTipoErradoListaValoresDoEnum() throws Exception {
        mvc.perform(get("/parametro").param("id", UUID.randomUUID().toString()).param("cor", "AZUL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.detail").value("Valor inválido para o parâmetro 'cor'. Valores aceitos: VERDE, CORAL."));
    }

    @Test
    void parametroObrigatorioAusente() throws Exception {
        mvc.perform(get("/parametro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_OBRIGATORIO"));
    }

    @Test
    void metodoNaoSuportado() throws Exception {
        mvc.perform(put("/corpo"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NAO_SUPORTADO"));
    }

    @Test
    void tipoDeConteudoNaoSuportado() throws Exception {
        mvc.perform(post("/corpo").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_DE_CONTEUDO_NAO_SUPORTADO"));
    }
}
