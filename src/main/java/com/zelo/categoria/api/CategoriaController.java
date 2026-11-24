package com.zelo.categoria.api;

import com.zelo.categoria.application.CategoriaService;
import com.zelo.categoria.domain.Categoria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Categorias")
@RestController
@RequestMapping("/api/v1/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService service;

    public record CategoriaResposta(UUID id, String nome, String descricao, String icone, boolean ativa) {

        static CategoriaResposta de(Categoria c) {
            return new CategoriaResposta(c.getId(), c.getNome(), c.getDescricao(), c.getIcone(), c.isAtiva());
        }
    }

    @Operation(summary = "Categorias ativas, para escolha ao registrar uma ocorrência")
    @GetMapping
    public List<CategoriaResposta> listar() {
        return service.listarAtivas().stream().map(CategoriaResposta::de).toList();
    }
}
