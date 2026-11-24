package com.zelo;

import com.jayway.jsonpath.JsonPath;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.infra.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: aplicação completa, segurança real e PostgreSQL com PostGIS.
 * <p>
 * O schema {@code zelo_teste} é apagado e recriado pelas migrations quando o contexto sobe (uma vez por
 * execução), com as cidades de {@code db/dados-teste}. Antes de cada teste, as tabelas de dados são esvaziadas;
 * estados, cidades, categorias, perfis e tipos de conteúdo ficam.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("teste")
@Import(TesteDeIntegracao.RecriacaoDoSchema.class)
public abstract class TesteDeIntegracao {

    protected static final int SAO_PAULO = 3550308;
    protected static final int RIO = 3304557;
    protected static final int CAMPINAS_INATIVA = 3509502;

    /** Pontos dentro dos limites de teste (V9000__dados_de_teste.sql). */
    protected static final Ponto PRACA_DA_SE = new Ponto(-23.5503, -46.6340);
    protected static final Ponto CENTRO_DO_RIO = new Ponto(-22.9068, -43.1729);
    protected static final Ponto CENTRO_DE_CAMPINAS = new Ponto(-22.9056, -47.0608);
    protected static final Ponto OCEANO = new Ponto(-25.5, -44.0);

    protected static final String SENHA = "Senha123";
    protected static final String BURACO_NA_VIA = "Buraco na via";
    protected static final String ILUMINACAO = "Iluminação pública";

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transacao;

    public record Ponto(double latitude, double longitude) {

        /** Ponto a {@code metros} ao norte deste. */
        public Ponto aoNorte(double metros) {
            return new Ponto(latitude + metros / 111_320.0, longitude);
        }
    }

    public record Conta(UUID id, String token, String refreshToken) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecriacaoDoSchema {

        @Bean
        FlywayMigrationStrategy recriarSchemaDeTeste() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @BeforeEach
    void esvaziarTabelasDeDados() {
        jdbc.execute("""
                TRUNCATE registro_auditoria, notificacao, preferencia_notificacao, ocorrencia_silenciada,
                         decisao_moderacao, denuncia, comentario, curtida, historico_status, foto_ocorrencia,
                         ocorrencia, consentimento, refresh_token, usuario, endereco CASCADE""");
    }

    // ------------------------------------------------------------------ contas

    protected static String emailUnico(String prefixo, String dominio) {
        return prefixo + "." + SEQUENCIA.incrementAndGet() + "." + System.nanoTime() + "@" + dominio;
    }

    protected static String endereco(int cidadeId) {
        return """
                {"cep": "01001-000", "rua": "Praça da Sé", "bairro": "Sé", "numero": "100", "cidadeId": %d}"""
                .formatted(cidadeId);
    }

    /** Cadastra um cidadão pela API (login automático). */
    protected Conta cidadao(int cidadeId) throws Exception {
        String email = emailUnico("cidadao", "gmail.com");
        String resposta = executar(post("/api/v1/auth/cadastro", null, """
                {"nome": "Cidadão de Teste", "email": "%s", "senha": "%s", "endereco": %s,
                 "aceiteTermosDeUso": true, "aceitePoliticaDePrivacidade": true}"""
                .formatted(email, SENHA, endereco(cidadeId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(resposta, "$.tokenDeAcesso");
        return new Conta(idDoToken(token), token, JsonPath.read(resposta, "$.refreshToken"));
    }

    protected Conta cidadao() throws Exception {
        return cidadao(SAO_PAULO);
    }

    /** Cria moderador, gestor ou administrador direto no banco e faz login pela API. */
    protected Conta membroDaEquipe(Perfil perfil, int cidadeId) throws Exception {
        String email = emailUnico(perfil.name().toLowerCase(), "zelo.app");
        transacao.executeWithoutResult(s -> usuarios.save(Usuario.novoMembroDaEquipe("Equipe " + perfil, email,
                passwordEncoder.encode(SENHA), perfil,
                new Endereco(new Endereco.Dados("01002020", "Viaduto do Chá", "Centro", "15", null, cidadeId),
                        Instant.now()), Instant.now())));
        return login(email, SENHA);
    }

    protected Conta login(String email, String senha) throws Exception {
        String resposta = executar(post("/api/v1/auth/login", null, """
                {"email": "%s", "senha": "%s"}""".formatted(email, senha)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(resposta, "$.tokenDeAcesso");
        return new Conta(idDoToken(token), token, JsonPath.read(resposta, "$.refreshToken"));
    }

    private UUID idDoToken(String token) throws Exception {
        String me = executar(get("/api/v1/me", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(me, "$.id"));
    }

    // ------------------------------------------------------------------ ocorrências

    protected UUID categoria(String nome) {
        return jdbc.queryForObject("SELECT id FROM categoria WHERE nome = ?", UUID.class, nome);
    }

    /** Ponto aleatório no centro de São Paulo, longe o bastante dos outros para não cair na regra de duplicidade. */
    protected static Ponto pontoLivreEmSaoPaulo() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new Ponto(-23.70 + r.nextDouble(0.25), -46.75 + r.nextDouble(0.30));
    }

    protected ResultActions registrarOcorrencia(Conta autor, String categoria, Ponto ponto, Ponto dispositivo,
                                                boolean ignorarSemelhantes) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/ocorrencias");
        req.file(new MockMultipartFile("fotos", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg()));
        req.param("categoriaId", categoria(categoria).toString())
                .param("descricao", "Buraco enorme no meio da rua")
                .param("latitude", String.valueOf(ponto.latitude()))
                .param("longitude", String.valueOf(ponto.longitude()))
                .param("latitudeDispositivo", String.valueOf(dispositivo.latitude()))
                .param("longitudeDispositivo", String.valueOf(dispositivo.longitude()))
                .param("ignorarSemelhantes", String.valueOf(ignorarSemelhantes));
        return executar(autenticar(req, autor.token()));
    }

    /** Registra uma ocorrência válida em São Paulo e devolve o id. */
    protected UUID ocorrencia(Conta autor, String categoria, Ponto ponto) throws Exception {
        String resposta = registrarOcorrencia(autor, categoria, ponto, ponto, false)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(resposta, "$.id"));
    }

    protected UUID ocorrencia(Conta autor) throws Exception {
        return ocorrencia(autor, BURACO_NA_VIA, pontoLivreEmSaoPaulo());
    }

    protected UUID comentario(Conta autor, UUID ocorrenciaId) throws Exception {
        String resposta = executar(post("/api/v1/ocorrencias/" + ocorrenciaId + "/comentarios", autor.token(), """
                {"texto": "Continua do mesmo jeito."}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(resposta, "$.id"));
    }

    protected static byte[] jpeg() {
        BufferedImage imagem = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        var g = imagem.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 64, 48);
        g.dispose();
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            ImageIO.write(imagem, "jpg", saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ requisições

    protected ResultActions executar(RequestBuilder req) throws Exception {
        return mvc.perform(req);
    }

    protected static MockHttpServletRequestBuilder get(String uri, String token) {
        return autenticar(request(HttpMethod.GET, uri).accept(MediaType.APPLICATION_JSON), token);
    }

    protected static MockHttpServletRequestBuilder post(String uri, String token, String json) {
        return comCorpo(HttpMethod.POST, uri, token, json);
    }

    protected static MockHttpServletRequestBuilder put(String uri, String token, String json) {
        return comCorpo(HttpMethod.PUT, uri, token, json);
    }

    protected static MockHttpServletRequestBuilder delete(String uri, String token) {
        return autenticar(request(HttpMethod.DELETE, uri), token);
    }

    private static MockHttpServletRequestBuilder comCorpo(HttpMethod metodo, String uri, String token, String json) {
        MockHttpServletRequestBuilder req = request(metodo, uri).accept(MediaType.APPLICATION_JSON);
        if (json != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return autenticar(req, token);
    }

    private static <T extends AbstractMockHttpServletRequestBuilder<T>> T autenticar(T req, String token) {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return req;
    }

    protected static <T> T ler(ResultActions resultado, String caminho) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), caminho);
    }

    protected long contar(String sql, Object... argumentos) {
        Long total = jdbc.queryForObject(sql, Long.class, argumentos);
        return total == null ? 0 : total;
    }

    protected Map<String, Object> linha(String sql, Object... argumentos) {
        return jdbc.queryForMap(sql, argumentos);
    }
}
