package com.zelo.usuario.api;

import com.zelo.compartilhado.api.PaginaResposta;
import com.zelo.compartilhado.api.Paginacao;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.compartilhado.seguranca.UsuarioAutenticado;
import com.zelo.usuario.api.UsuarioDtos.AlterarPerfilRequest;
import com.zelo.usuario.api.UsuarioDtos.EnderecoRequest;
import com.zelo.usuario.api.UsuarioDtos.NovoMembroDaEquipeRequest;
import com.zelo.usuario.api.UsuarioDtos.UsuarioAdminResposta;
import com.zelo.usuario.application.AdminUsuarioService;
import com.zelo.usuario.application.EnderecoService;
import com.zelo.usuario.domain.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Clock;
import java.util.UUID;

@Tag(name = "Admin: usuários")
@RestController
@RequestMapping("/api/v1/admin/usuarios")
@RequiredArgsConstructor
public class AdminUsuarioController {

    private final AdminUsuarioService service;
    private final EnderecoService enderecoService;
    private final ProvedorDeAtor provedorDeAtor;
    private final Clock clock;

    @Operation(summary = "Pesquisa usuários por perfil, cidade do endereço e nome/e-mail")
    @GetMapping
    public PaginaResposta<UsuarioAdminResposta> pesquisar(@RequestParam(required = false) Perfil perfil,
                                                          @RequestParam(required = false) Integer cidadeId,
                                                          @RequestParam(required = false) String termo,
                                                          @RequestParam(defaultValue = "false") boolean incluirExcluidos,
                                                          @RequestParam(defaultValue = "0") int pagina,
                                                          @RequestParam(defaultValue = "20") int tamanho) {
        return PaginaResposta.de(service.pesquisar(perfil, cidadeId, termo, incluirExcluidos,
                Paginacao.de(pagina, tamanho)), this::resposta);
    }

    @GetMapping("/{id}")
    public UsuarioAdminResposta obter(@PathVariable UUID id) {
        return resposta(service.obter(id));
    }

    @Operation(summary = "Cria conta de moderador, gestor ou administrador",
            description = "Para moderador e gestor, a cidade do endereço define a área de atuação.")
    @PostMapping
    public ResponseEntity<UsuarioAdminResposta> criar(@Valid @RequestBody NovoMembroDaEquipeRequest req,
                                                      @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Usuario criado = service.criarMembroDaEquipe(req.nome(), req.email(), req.senha(), req.perfil(),
                req.endereco().paraDados(), provedorDeAtor.obter(usuario.id()));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(criado.getId()).toUri()).body(resposta(criado));
    }

    @Operation(summary = "Altera o perfil do usuário")
    @PutMapping("/{id}/perfil")
    public UsuarioAdminResposta alterarPerfil(@PathVariable UUID id, @Valid @RequestBody AlterarPerfilRequest req,
                                              @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return resposta(service.alterarPerfil(id, req.perfil(), provedorDeAtor.obter(usuario.id())));
    }

    @Operation(summary = "Altera o endereço do usuário (para a equipe, muda a cidade de atuação)")
    @PutMapping("/{id}/endereco")
    public UsuarioAdminResposta alterarEndereco(@PathVariable UUID id, @Valid @RequestBody EnderecoRequest req,
                                                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return resposta(service.alterarEndereco(id, req.paraDados(), provedorDeAtor.obter(usuario.id())));
    }

    private UsuarioAdminResposta resposta(Usuario u) {
        return UsuarioAdminResposta.de(u, enderecoService.visao(u.getEndereco()), clock.instant());
    }
}
