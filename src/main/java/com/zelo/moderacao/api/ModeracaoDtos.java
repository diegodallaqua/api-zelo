package com.zelo.moderacao.api;

import com.zelo.moderacao.domain.DecisaoModeracao;
import com.zelo.moderacao.domain.Denuncia;
import com.zelo.moderacao.domain.TipoConteudo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ModeracaoDtos {

    private ModeracaoDtos() {
    }

    /** Informe exatamente um entre {@code ocorrenciaId} e {@code comentarioId}. */
    public record DenunciaRequest(
            UUID ocorrenciaId,
            UUID comentarioId,
            @NotNull(message = "O motivo é obrigatório.") Denuncia.Motivo motivo,
            @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.") String descricao) {
    }

    /** Resposta ao denunciante: não expõe dados de terceiros. */
    public record DenunciaResposta(UUID id, TipoConteudo tipoConteudo, UUID ocorrenciaId, UUID comentarioId,
                                   Denuncia.Motivo motivo, String descricao, Denuncia.Status status,
                                   Instant criadaEm, Instant atualizadaEm) {

        static DenunciaResposta de(Denuncia d) {
            return new DenunciaResposta(d.getId(), d.getTipoConteudo(), d.getOcorrenciaId(), d.getComentarioId(),
                    d.getMotivo(), d.getDescricao(), d.getStatus(), d.getCriadaEm(), d.getAtualizadaEm());
        }
    }

    public record DenunciaDetalheResposta(UUID id, Denuncia.Motivo motivo, String descricao, Denuncia.Status status,
                                          Instant criadaEm, Instant resolvidaEm) {

        static DenunciaDetalheResposta de(Denuncia d) {
            return new DenunciaDetalheResposta(d.getId(), d.getMotivo(), d.getDescricao(), d.getStatus(), d.getCriadaEm(),
                    d.getResolvidaEm());
        }
    }

    /** Informe exatamente um entre {@code ocorrenciaId} e {@code comentarioId}. */
    public record DecisaoRequest(
            UUID ocorrenciaId,
            UUID comentarioId,
            @NotNull(message = "A ação é obrigatória.") DecisaoModeracao.Acao acao,
            @NotBlank(message = "O motivo da decisão é obrigatório.")
            @Size(max = 2000, message = "O motivo deve ter no máximo 2000 caracteres.") String motivo,
            UUID ocorrenciaPrincipalId,
            Instant suspensaoAte,
            Boolean suspensaoPermanente,
            Boolean removerConteudo) {
    }

    public record MesclagemRequest(
            @NotNull(message = "Informe a ocorrência principal.") UUID ocorrenciaPrincipalId,
            @NotBlank(message = "O motivo é obrigatório.")
            @Size(max = 2000, message = "O motivo deve ter no máximo 2000 caracteres.") String motivo) {
    }

    public record SuspensaoRequest(
            Instant ate,
            Boolean permanente,
            @NotBlank(message = "O motivo é obrigatório.")
            @Size(max = 2000, message = "O motivo deve ter no máximo 2000 caracteres.") String motivo) {
    }

    public record ReativacaoRequest(
            @NotBlank(message = "O motivo é obrigatório.")
            @Size(max = 2000, message = "O motivo deve ter no máximo 2000 caracteres.") String motivo) {
    }

    public record DecisaoResposta(UUID id, UUID moderadorId, DecisaoModeracao.Acao acao, TipoConteudo tipoConteudo,
                                  UUID ocorrenciaId, UUID comentarioId, UUID usuarioAfetadoId,
                                  UUID ocorrenciaPrincipalId, Instant suspensaoAte, boolean suspensaoPermanente,
                                  String motivo, Instant decididaEm) {

        static DecisaoResposta de(DecisaoModeracao d) {
            return new DecisaoResposta(d.getId(), d.getModeradorId(), d.getAcao(), d.getTipoConteudo(),
                    d.getOcorrenciaId(), d.getComentarioId(), d.getUsuarioAfetadoId(), d.getOcorrenciaPrincipalId(),
                    d.getSuspensaoAte(), d.isSuspensaoPermanente(), d.getMotivo(), d.getDecididaEm());
        }
    }
}
