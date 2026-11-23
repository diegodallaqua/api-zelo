package com.zelo.usuario.api;

import com.zelo.usuario.api.UsuarioDtos.CadastroRequest;
import com.zelo.usuario.api.UsuarioDtos.LoginRequest;
import com.zelo.usuario.api.UsuarioDtos.RefreshTokenRequest;
import com.zelo.usuario.application.AutenticacaoService;
import com.zelo.usuario.application.TokenService.Tokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AutenticacaoController {

    private final AutenticacaoService service;

    @Operation(summary = "Cadastro de cidadão (e-mail dos domínios permitidos e endereço obrigatório) e login automático")
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public Tokens cadastrar(@Valid @RequestBody CadastroRequest req) {
        return service.cadastrar(req.nome(), req.email(), req.senha(), req.endereco().paraDados(),
                req.aceiteTermosDeUso(), req.aceitePoliticaDePrivacidade());
    }

    @Operation(summary = "Login com e-mail e senha")
    @PostMapping("/login")
    public Tokens login(@Valid @RequestBody LoginRequest req) {
        return service.login(req.email(), req.senha());
    }

    @Operation(summary = "Renova o token de acesso (o refresh token usado é invalidado)")
    @PostMapping("/renovacao")
    public Tokens renovar(@Valid @RequestBody RefreshTokenRequest req) {
        return service.renovar(req.refreshToken());
    }

    @Operation(summary = "Encerra a sessão revogando o refresh token")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest req) {
        service.logout(req.refreshToken());
    }
}
