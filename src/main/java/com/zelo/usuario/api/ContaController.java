package com.zelo.usuario.api;

import com.zelo.compartilhado.seguranca.UsuarioAutenticado;
import com.zelo.usuario.api.UsuarioDtos.AlterarSenhaRequest;
import com.zelo.usuario.api.UsuarioDtos.AtualizarContaRequest;
import com.zelo.usuario.api.UsuarioDtos.ConsentimentoRequest;
import com.zelo.usuario.api.UsuarioDtos.ConsentimentoResposta;
import com.zelo.usuario.api.UsuarioDtos.ContaResposta;
import com.zelo.usuario.api.UsuarioDtos.EnderecoRequest;
import com.zelo.usuario.api.UsuarioDtos.ExcluirContaRequest;
import com.zelo.usuario.application.ContaService;
import com.zelo.usuario.application.EnderecoService;
import com.zelo.usuario.domain.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

@Tag(name = "Minha conta")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class ContaController {

    private final ContaService service;
    private final EnderecoService enderecoService;
    private final Clock clock;

    @Operation(summary = "Dados da conta autenticada, com endereço")
    @GetMapping
    public ContaResposta obter(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return resposta(service.obter(usuario.id()));
    }

    @Operation(summary = "Altera o nome de exibição")
    @PatchMapping
    public ContaResposta atualizar(@Valid @RequestBody AtualizarContaRequest req,
                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return resposta(service.alterarNome(usuario.id(), req.nome()));
    }

    @Operation(summary = "Altera o próprio endereço (moderadores e gestores: só pelo administrador)")
    @PutMapping("/endereco")
    public ContaResposta alterarEndereco(@Valid @RequestBody EnderecoRequest req,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return resposta(service.alterarEndereco(usuario.id(), req.paraDados()));
    }

    @Operation(summary = "Troca a senha e encerra todas as sessões")
    @PutMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(@Valid @RequestBody AlterarSenhaRequest req,
                             @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.alterarSenha(usuario.id(), req.senhaAtual(), req.novaSenha());
    }

    @Operation(summary = "Exclui a conta e anonimiza os dados pessoais (LGPD). Irreversível.")
    @PostMapping("/exclusao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@Valid @RequestBody ExcluirContaRequest req,
                        @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.excluirConta(usuario.id(), req.senha());
    }

    @Operation(summary = "Histórico de consentimentos (termos, privacidade, localização)")
    @GetMapping("/consentimentos")
    public List<ConsentimentoResposta> consentimentos(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.listarConsentimentos(usuario.id()).stream().map(ConsentimentoResposta::de).toList();
    }

    @Operation(summary = "Registra um consentimento ou sua revogação (ex.: uso da localização)")
    @PostMapping("/consentimentos")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentimentoResposta registrarConsentimento(@Valid @RequestBody ConsentimentoRequest req,
                                                        @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ConsentimentoResposta.de(service.registrarConsentimento(usuario.id(), req.tipo(), req.aceito()));
    }

    private ContaResposta resposta(Usuario u) {
        return ContaResposta.de(u, enderecoService.visao(u.getEndereco()), clock.instant());
    }
}
