package com.zelo.localidade.infra;

import com.zelo.compartilhado.erro.ServicoIndisponivelException;
import com.zelo.localidade.application.LocalidadeProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cliente das APIs públicas de localidades e malhas do IBGE. */
@Component
public class ClienteIbge {

    private static final MediaType GEOJSON = MediaType.valueOf("application/vnd.geo+json");

    private final RestClient restClient;
    private final String qualidadeMalha;

    public ClienteIbge(LocalidadeProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.ibge().timeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.ibge().urlBase())
                .requestFactory(requestFactory)
                .build();
        this.qualidadeMalha = properties.ibge().qualidadeMalha();
    }

    public record EstadoIbge(int id, String nome) {
    }

    public record CidadeIbge(int id, String nome, int estadoId) {
    }

    public List<EstadoIbge> listarEstados() {
        List<EstadoIbge> estados = new ArrayList<>();
        for (JsonNode e : get("/v1/localidades/estados", MediaType.APPLICATION_JSON)) {
            estados.add(new EstadoIbge(e.path("id").asInt(), e.path("nome").asString()));
        }
        return estados;
    }

    public List<CidadeIbge> listarCidades() {
        List<CidadeIbge> cidades = new ArrayList<>();
        for (JsonNode m : get("/v1/localidades/municipios", MediaType.APPLICATION_JSON)) {
            // "regiao-imediata" existe para todas; "microrregiao" é nula em cidades criadas recentemente.
            JsonNode uf = m.path("regiao-imediata").path("regiao-intermediaria").path("UF");
            if (uf.isMissingNode() || uf.isNull()) {
                uf = m.path("microrregiao").path("mesorregiao").path("UF");
            }
            cidades.add(new CidadeIbge(m.path("id").asInt(), m.path("nome").asString(), uf.path("id").asInt()));
        }
        return cidades;
    }

    /** Geometria GeoJSON de cada cidade do estado, indexada pelo código IBGE. */
    public Map<Integer, String> malhasDasCidadesDoEstado(int estadoId) {
        JsonNode raiz = get("/v3/malhas/estados/" + estadoId
                + "?formato=application/vnd.geo+json&intrarregiao=municipio&qualidade=" + qualidadeMalha, GEOJSON);
        Map<Integer, String> malhas = new HashMap<>();
        for (JsonNode feature : raiz.path("features")) {
            int codigo = Integer.parseInt(feature.path("properties").path("codarea").asString());
            malhas.put(codigo, feature.path("geometry").toString());
        }
        return malhas;
    }

    private JsonNode get(String uri, MediaType aceita) {
        try {
            JsonNode corpo = restClient.get().uri(uri).accept(aceita, MediaType.APPLICATION_JSON)
                    .retrieve().body(JsonNode.class);
            if (corpo == null) {
                throw new ServicoIndisponivelException("IBGE_INDISPONIVEL", "O IBGE retornou uma resposta vazia.");
            }
            return corpo;
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("IBGE_INDISPONIVEL",
                    "Não foi possível consultar a API do IBGE: " + e.getMessage());
        }
    }
}
