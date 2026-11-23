package com.zelo.ocorrencia.api;

import com.zelo.compartilhado.seguranca.UsuarioAutenticado;
import com.zelo.ocorrencia.api.OcorrenciaDtos.ComentarioRequest;
import com.zelo.ocorrencia.application.ComentarioService;
import com.zelo.ocorrencia.application.Visoes.ComentarioVisao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Comentários")
@RestController
@RequestMapping("/api/v1/comentarios")
@RequiredArgsConstructor
public class ComentarioController {

    private final ComentarioService service;

    @Operation(summary = "Edita o próprio comentário")
    @PatchMapping("/{id}")
    public ComentarioVisao editar(@PathVariable UUID id, @Valid @RequestBody ComentarioRequest req,
                                  @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.editar(id, usuario.id(), req.texto());
    }

    @Operation(summary = "Exclui o próprio comentário")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.excluir(id, usuario.id());
    }
}
