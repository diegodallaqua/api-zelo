package com.zelo.notificacao.api;

import com.zelo.compartilhado.api.PaginaResposta;
import com.zelo.compartilhado.api.Paginacao;
import com.zelo.compartilhado.seguranca.UsuarioAutenticado;
import com.zelo.notificacao.application.NotificacaoService;
import com.zelo.notificacao.domain.Notificacao;
import com.zelo.notificacao.domain.PreferenciaNotificacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Notificações")
@RestController
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoService service;

    public record NotificacaoResposta(UUID id, Notificacao.Tipo tipo, String titulo, String mensagem,
                                      UUID ocorrenciaId, boolean lida, Instant criadaEm) {

        static NotificacaoResposta de(Notificacao n) {
            return new NotificacaoResposta(n.getId(), n.getTipo(), n.getTitulo(), n.getMensagem(), n.getOcorrenciaId(),
                    n.getLidaEm() != null, n.getCriadaEm());
        }
    }

    public record TotalResposta(long total) {
    }

    public record PreferenciasRequest(@NotNull(message = "Informe se deseja receber push.") Boolean push,
                                      @NotNull(message = "Informe se deseja receber e-mail.") Boolean email) {
    }

    public record PreferenciasResposta(boolean push, boolean email) {

        static PreferenciasResposta de(PreferenciaNotificacao p) {
            return new PreferenciasResposta(p.isPush(), p.isEmail());
        }
    }

    @Operation(summary = "Notificações do usuário, mais recentes primeiro")
    @GetMapping("/api/v1/me/notificacoes")
    public PaginaResposta<NotificacaoResposta> listar(@RequestParam(defaultValue = "false") boolean somenteNaoLidas,
                                                      @RequestParam(defaultValue = "0") int pagina,
                                                      @RequestParam(defaultValue = "20") int tamanho,
                                                      @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PaginaResposta.de(service.listar(usuario.id(), somenteNaoLidas, Paginacao.de(pagina, tamanho)),
                NotificacaoResposta::de);
    }

    @Operation(summary = "Quantidade de notificações não lidas")
    @GetMapping("/api/v1/me/notificacoes/nao-lidas")
    public TotalResposta naoLidas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new TotalResposta(service.contarNaoLidas(usuario.id()));
    }

    @Operation(summary = "Marca uma notificação como lida")
    @PostMapping("/api/v1/me/notificacoes/{id}/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarComoLida(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.marcarComoLida(id, usuario.id());
    }

    @Operation(summary = "Marca todas as notificações como lidas")
    @PostMapping("/api/v1/me/notificacoes/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarTodasComoLidas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.marcarTodasComoLidas(usuario.id());
    }

    @Operation(summary = "Canais de notificação escolhidos (push, e-mail)")
    @GetMapping("/api/v1/me/preferencias-notificacao")
    public PreferenciasResposta preferencias(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PreferenciasResposta.de(service.preferencias(usuario.id()));
    }

    @Operation(summary = "Altera os canais de notificação")
    @PutMapping("/api/v1/me/preferencias-notificacao")
    public PreferenciasResposta alterarPreferencias(@Valid @RequestBody PreferenciasRequest req,
                                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PreferenciasResposta.de(service.alterarPreferencias(usuario.id(), req.push(), req.email()));
    }

    @Operation(summary = "Ocorrências silenciadas pelo usuário")
    @GetMapping("/api/v1/me/ocorrencias-silenciadas")
    public List<UUID> silenciadas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.silenciadas(usuario.id());
    }

    @Operation(summary = "Silencia as notificações de uma ocorrência")
    @PutMapping("/api/v1/ocorrencias/{id}/silenciamento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void silenciar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.silenciar(usuario.id(), id);
    }

    @Operation(summary = "Volta a receber notificações de uma ocorrência")
    @DeleteMapping("/api/v1/ocorrencias/{id}/silenciamento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reativar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.reativar(usuario.id(), id);
    }
}
