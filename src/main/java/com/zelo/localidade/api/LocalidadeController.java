package com.zelo.localidade.api;

import com.zelo.compartilhado.api.PaginaResposta;
import com.zelo.compartilhado.api.Paginacao;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.localidade.api.LocalidadeDtos.CidadeDetalheResposta;
import com.zelo.localidade.api.LocalidadeDtos.CidadeResposta;
import com.zelo.localidade.api.LocalidadeDtos.EstadoResposta;
import com.zelo.localidade.application.CidadeService;
import com.zelo.localidade.domain.Cidade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Estados e cidades")
@RestController
@RequiredArgsConstructor
public class LocalidadeController {

    private final CidadeService service;

    @Operation(summary = "Lista os estados")
    @GetMapping("/api/v1/estados")
    public List<EstadoResposta> estados() {
        return service.listarEstados().stream().map(EstadoResposta::de).toList();
    }

    @Operation(summary = "Pesquisa cidades por nome (sem acento) e estado")
    @GetMapping("/api/v1/cidades")
    public PaginaResposta<CidadeResposta> pesquisar(@RequestParam(required = false) String nome,
                                                    @RequestParam(required = false) Integer estadoId,
                                                    @RequestParam(defaultValue = "0") int pagina,
                                                    @RequestParam(defaultValue = "20") int tamanho) {
        return PaginaResposta.de(service.pesquisar(nome, estadoId, Paginacao.de(pagina, tamanho)), CidadeResposta::de);
    }

    @Operation(summary = "Cidade que contém o ponto informado (ex.: localização do usuário)")
    @GetMapping("/api/v1/cidades/localizacao")
    public CidadeDetalheResposta localizar(
            @RequestParam @DecimalMin(value = "-90", message = "Latitude deve estar entre -90 e 90.")
            @DecimalMax(value = "90", message = "Latitude deve estar entre -90 e 90.") double latitude,
            @RequestParam @DecimalMin(value = "-180", message = "Longitude deve estar entre -180 e 180.")
            @DecimalMax(value = "180", message = "Longitude deve estar entre -180 e 180.") double longitude) {
        Cidade cidade = service.localizar(latitude, longitude)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CIDADE_NAO_ENCONTRADA",
                        "Nenhuma cidade cadastrada contém este ponto."));
        return detalhe(cidade);
    }

    @Operation(summary = "Detalhes da cidade: textos legais, aviso de canal não oficial e limites do mapa")
    @GetMapping("/api/v1/cidades/{id}")
    public CidadeDetalheResposta obter(@PathVariable Integer id) {
        return detalhe(service.obter(id));
    }

    private CidadeDetalheResposta detalhe(Cidade c) {
        return new CidadeDetalheResposta(c.getId(), c.getNome(), EstadoResposta.de(c.getEstado()), c.isAtivo(),
                service.avisoCanalNaoOficial(c), c.getUrlCanalOficial(), c.getTextoLegal(), service.limites(c.getId()));
    }
}
