package com.zelo.categoria.api;

import com.zelo.categoria.api.CategoriaController.CategoriaResposta;
import com.zelo.categoria.application.CategoriaService;
import com.zelo.categoria.domain.Categoria;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.compartilhado.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin: categorias")
@RestController
@RequestMapping("/api/v1/admin/categorias")
@RequiredArgsConstructor
public class AdminCategoriaController {

    private final CategoriaService service;
    private final ProvedorDeAtor provedorDeAtor;

    public record NovaCategoriaRequest(
            @NotBlank(message = "O nome é obrigatório.") @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres.") String nome,
            @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.") String descricao,
            @Size(max = 60, message = "O ícone deve ter no máximo 60 caracteres.") String icone) {
    }

    public record AtualizarCategoriaRequest(
            @NotBlank(message = "O nome é obrigatório.") @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres.") String nome,
            @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.") String descricao,
            @Size(max = 60, message = "O ícone deve ter no máximo 60 caracteres.") String icone,
            @NotNull(message = "Informe se a categoria está ativa.") Boolean ativa) {
    }

    @Operation(summary = "Lista todas as categorias, ativas e inativas")
    @GetMapping
    public List<CategoriaResposta> listar() {
        return service.listarTodas().stream().map(CategoriaResposta::de).toList();
    }

    @Operation(summary = "Cria categoria")
    @PostMapping
    public ResponseEntity<CategoriaResposta> criar(@Valid @RequestBody NovaCategoriaRequest req,
                                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Categoria categoria = service.criar(req.nome(), req.descricao(), req.icone(), provedorDeAtor.obter(usuario.id()));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(categoria.getId()).toUri()).body(CategoriaResposta.de(categoria));
    }

    @GetMapping("/{id}")
    public CategoriaResposta obter(@PathVariable UUID id) {
        return CategoriaResposta.de(service.obter(id));
    }

    @Operation(summary = "Atualiza nome, descrição, ícone e situação (ativa/inativa)")
    @PutMapping("/{id}")
    public CategoriaResposta atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizarCategoriaRequest req,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return CategoriaResposta.de(service.atualizar(id, req.nome(), req.descricao(), req.icone(), req.ativa(),
                provedorDeAtor.obter(usuario.id())));
    }

    @Operation(summary = "Exclui categoria nunca usada (409 se já usada: desative-a)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.excluir(id, provedorDeAtor.obter(usuario.id()));
        return ResponseEntity.noContent().build();
    }
}
