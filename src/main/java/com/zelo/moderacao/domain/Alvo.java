package com.zelo.moderacao.domain;

import com.zelo.compartilhado.erro.RequisicaoInvalidaException;

import java.util.UUID;

/**
 * Conteúdo alvo de denúncia ou decisão: exatamente uma ocorrência OU um comentário, nunca os dois.
 * A mesma regra é garantida no banco pelas constraints {@code ck_denuncia_alvo} e {@code ck_decisao_alvo}.
 */
public record Alvo(TipoConteudo tipo, UUID ocorrenciaId, UUID comentarioId) {

    public static Alvo de(UUID ocorrenciaId, UUID comentarioId) {
        if ((ocorrenciaId == null) == (comentarioId == null)) {
            throw new RequisicaoInvalidaException("ALVO_INVALIDO",
                    "Informe exatamente um entre 'ocorrenciaId' e 'comentarioId', nunca os dois.");
        }
        return ocorrenciaId != null
                ? new Alvo(TipoConteudo.OCORRENCIA, ocorrenciaId, null)
                : new Alvo(TipoConteudo.COMENTARIO, null, comentarioId);
    }

    public static Alvo ocorrencia(UUID id) {
        return new Alvo(TipoConteudo.OCORRENCIA, id, null);
    }

    public static Alvo comentario(UUID id) {
        return new Alvo(TipoConteudo.COMENTARIO, null, id);
    }

    /** Id do conteúdo, seja ocorrência ou comentário. */
    public UUID id() {
        return tipo == TipoConteudo.OCORRENCIA ? ocorrenciaId : comentarioId;
    }
}
