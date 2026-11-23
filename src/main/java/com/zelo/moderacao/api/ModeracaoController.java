package com.zelo.moderacao.api;

import com.zelo.compartilhado.api.PaginaResposta;
import com.zelo.compartilhado.api.Paginacao;
import com.zelo.compartilhado.seguranca.UsuarioAutenticado;
import com.zelo.moderacao.api.ModeracaoDtos.DecisaoRequest;
import com.zelo.moderacao.api.ModeracaoDtos.DecisaoResposta;
import com.zelo.moderacao.api.ModeracaoDtos.DenunciaDetalheResposta;
import com.zelo.moderacao.api.ModeracaoDtos.DenunciaRequest;
import com.zelo.moderacao.api.ModeracaoDtos.DenunciaResposta;
import com.zelo.moderacao.api.ModeracaoDtos.MesclagemRequest;
import com.zelo.moderacao.api.ModeracaoDtos.ReativacaoRequest;
import com.zelo.moderacao.api.ModeracaoDtos.SuspensaoRequest;
import com.zelo.moderacao.application.DenunciaService;
import com.zelo.moderacao.application.ModeracaoService;
import com.zelo.moderacao.application.ModeracaoService.Decisao;
import com.zelo.moderacao.application.ModeracaoService.ItemDaFilaVisao;
import com.zelo.moderacao.domain.Alvo;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ModeracaoController {

    private static final String REGRA_ALVO = "Informe exatamente um entre 'ocorrenciaId' e 'comentarioId' "
            + "(400 ALVO_INVALIDO se vierem os dois ou nenhum).";

    private final DenunciaService denunciaService;
    private final ModeracaoService moderacao;

    @Tag(name = "Denúncias")
    @Operation(summary = "Denuncia uma ocorrência ou um comentário", description = REGRA_ALVO)
    @PostMapping("/api/v1/denuncias")
    @ResponseStatus(HttpStatus.CREATED)
    public DenunciaResposta denunciar(@Valid @RequestBody DenunciaRequest req,
                                      @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return DenunciaResposta.de(denunciaService.denunciar(usuario.id(), Alvo.de(req.ocorrenciaId(), req.comentarioId()),
                req.motivo(), req.descricao()));
    }

    @Tag(name = "Denúncias")
    @Operation(summary = "Altera a própria denúncia enquanto ela está pendente", description = REGRA_ALVO)
    @PutMapping("/api/v1/denuncias/{id}")
    public DenunciaResposta alterar(@PathVariable UUID id, @Valid @RequestBody DenunciaRequest req,
                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return DenunciaResposta.de(denunciaService.alterar(id, usuario.id(),
                Alvo.de(req.ocorrenciaId(), req.comentarioId()), req.motivo(), req.descricao()));
    }

    @Tag(name = "Moderação")
    @Operation(summary = "Fila de conteúdos com denúncias pendentes, mais denunciados primeiro")
    @GetMapping("/api/v1/moderacao/denuncias")
    public PaginaResposta<ItemDaFilaVisao> fila(@RequestParam(required = false) Integer cidadeId,
                                                @RequestParam(defaultValue = "0") int pagina,
                                                @RequestParam(defaultValue = "20") int tamanho,
                                                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PaginaResposta.de(moderacao.fila(usuario.id(), cidadeId, Paginacao.de(pagina, tamanho)));
    }

    @Tag(name = "Moderação")
    @Operation(summary = "Denúncias recebidas por uma ocorrência")
    @GetMapping("/api/v1/moderacao/ocorrencias/{id}/denuncias")
    public List<DenunciaDetalheResposta> denunciasDaOcorrencia(@PathVariable UUID id,
                                                               @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return moderacao.denunciasDoConteudo(usuario.id(), Alvo.ocorrencia(id)).stream()
                .map(DenunciaDetalheResposta::de).toList();
    }

    @Tag(name = "Moderação")
    @Operation(summary = "Denúncias recebidas por um comentário")
    @GetMapping("/api/v1/moderacao/comentarios/{id}/denuncias")
    public List<DenunciaDetalheResposta> denunciasDoComentario(@PathVariable UUID id,
                                                               @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return moderacao.denunciasDoConteudo(usuario.id(), Alvo.comentario(id)).stream()
                .map(DenunciaDetalheResposta::de).toList();
    }

    @Tag(name = "Moderação")
    @Operation(summary = "Decide as denúncias de um conteúdo: MANTER, REMOVER_CONTEUDO, MESCLAR_DUPLICATA ou SUSPENDER_AUTOR",
            description = REGRA_ALVO + " Decisões são imutáveis: não há PUT.")
    @PostMapping("/api/v1/moderacao/decisoes")
    @ResponseStatus(HttpStatus.CREATED)
    public DecisaoResposta decidir(@Valid @RequestBody DecisaoRequest req,
                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return DecisaoResposta.de(moderacao.decidir(usuario.id(), new Decisao(
                Alvo.de(req.ocorrenciaId(), req.comentarioId()), req.acao(), req.motivo(), req.ocorrenciaPrincipalId(),
                req.suspensaoAte(), Boolean.TRUE.equals(req.suspensaoPermanente()),
                Boolean.TRUE.equals(req.removerConteudo()))));
    }

    @Tag(name = "Moderação")
    @Operation(summary = "Mescla uma ocorrência duplicada na principal")
    @PostMapping("/api/v1/moderacao/ocorrencias/{id}/mesclagem")
    @ResponseStatus(HttpStatus.CREATED)
    public DecisaoResposta mesclar(@PathVariable UUID id, @Valid @RequestBody MesclagemRequest req,
                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return DecisaoResposta.de(moderacao.mesclar(usuario.id(), id, req.ocorrenciaPrincipalId(), req.motivo()));
    }

    @Tag(name = "Moderação")
    @Operation(summary = "Suspende um usuário temporária (ate) ou permanentemente")
    @PostMapping("/api/v1/moderacao/usuarios/{id}/suspensao")
    @ResponseStatus(HttpStatus.CREATED)
    public DecisaoResposta suspender(@PathVariable UUID id, @Valid @RequestBody SuspensaoRequest req,
                                     @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return DecisaoResposta.de(moderacao.suspender(usuario.id(), id, req.ate(),
                Boolean.TRUE.equals(req.permanente()), req.motivo()));
    }

    @Tag(name = "Moderação")
    @Operation(summary = "Encerra a suspensão de um usuário")
    @PostMapping("/api/v1/moderacao/usuarios/{id}/reativacao")
    @ResponseStatus(HttpStatus.CREATED)
    public DecisaoResposta reativar(@PathVariable UUID id, @Valid @RequestBody ReativacaoRequest req,
                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return DecisaoResposta.de(moderacao.reativar(usuario.id(), id, req.motivo()));
    }

    @Tag(name = "Moderação")
    @Operation(summary = "Histórico de decisões sobre o usuário (reincidência)")
    @GetMapping("/api/v1/moderacao/usuarios/{id}/decisoes")
    public List<DecisaoResposta> historico(@PathVariable UUID id) {
        return moderacao.historicoDoUsuario(id).stream().map(DecisaoResposta::de).toList();
    }
}
