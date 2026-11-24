package com.zelo.ocorrencia.domain.evento;

import com.zelo.ocorrencia.domain.HistoricoStatus.TipoEvento;
import com.zelo.ocorrencia.domain.StatusOcorrencia;

import java.util.UUID;

/** Eventos de domínio publicados pelo módulo de ocorrências (consumidos, por exemplo, pelas notificações). */
public final class EventosDeOcorrencia {

    private EventosDeOcorrencia() {
    }

    public record OcorrenciaCriada(UUID ocorrenciaId, Integer cidadeId, UUID categoriaId) {
    }

    /** @param autorOcorrenciaId nulo se a ocorrência foi anonimizada */
    public record StatusAlterado(UUID ocorrenciaId, UUID autorOcorrenciaId, StatusOcorrencia anterior,
                                 StatusOcorrencia novo, TipoEvento tipo, String motivo) {
    }

    public record OcorrenciaMesclada(UUID duplicadaId, UUID principalId, UUID autorDuplicadaId) {
    }

    public record AvisoDeArquivamento(UUID ocorrenciaId, UUID autorId) {
    }

    /** @param tipoConteudo OCORRENCIA ou COMENTARIO */
    public record ConteudoRemovidoPelaModeracao(String tipoConteudo, UUID conteudoId, UUID ocorrenciaId,
                                                UUID autorId, String motivo) {
    }
}
