package com.zelo.ocorrencia.application;

import com.zelo.ocorrencia.infra.ComentarioRepository;
import com.zelo.ocorrencia.infra.OcorrenciaRepository;
import com.zelo.usuario.domain.EventosDeUsuario.ContaExcluida;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * LGPD: ao excluir a conta, ocorrências e comentários continuam no mapa, mas sem vínculo com o autor.
 * Roda na mesma transação da exclusão.
 */
@Component
@RequiredArgsConstructor
class OuvinteDeContaExcluida {

    private final OcorrenciaRepository ocorrencias;
    private final ComentarioRepository comentarios;

    @EventListener
    void aoExcluirConta(ContaExcluida evento) {
        ocorrencias.anonimizarAutor(evento.usuarioId());
        comentarios.anonimizarAutor(evento.usuarioId());
    }
}
