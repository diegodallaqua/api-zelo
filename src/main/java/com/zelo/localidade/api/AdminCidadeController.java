package com.zelo.localidade.api;

import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.compartilhado.seguranca.UsuarioAutenticado;
import com.zelo.localidade.api.LocalidadeDtos.CidadeResposta;
import com.zelo.localidade.api.LocalidadeDtos.ConfigurarCidadeRequest;
import com.zelo.localidade.application.CidadeService;
import com.zelo.localidade.application.ImportacaoDeCidadesService;
import com.zelo.localidade.application.ImportacaoDeCidadesService.EstadoImportacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: cidades")
@RestController
@RequestMapping("/api/v1/admin/cidades")
@RequiredArgsConstructor
public class AdminCidadeController {

    private final CidadeService service;
    private final ImportacaoDeCidadesService importacao;
    private final ProvedorDeAtor provedorDeAtor;

    @Operation(summary = "Ativa/desativa a cidade e define seus textos legais e canal oficial")
    @PutMapping("/{id}")
    public CidadeResposta configurar(@PathVariable Integer id, @Valid @RequestBody ConfigurarCidadeRequest req,
                                     @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return CidadeResposta.de(service.configurar(id, req.ativo(), req.avisoCanalNaoOficial(),
                req.urlCanalOficial(), req.textoLegal(), provedorDeAtor.obter(usuario.id())));
    }

    @Operation(summary = "Atualiza estados e cidades a partir do IBGE, em segundo plano")
    @PostMapping("/importacao")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EstadoImportacao importar() {
        return importacao.iniciar();
    }

    @Operation(summary = "Situação da última importação de estados e cidades")
    @GetMapping("/importacao")
    public EstadoImportacao estadoDaImportacao() {
        return importacao.estado();
    }
}
