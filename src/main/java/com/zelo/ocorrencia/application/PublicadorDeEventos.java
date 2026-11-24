package com.zelo.ocorrencia.application;

import com.zelo.ocorrencia.domain.Ocorrencia;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publica os eventos acumulados no agregado. Chamado dentro da transação: os ouvintes de notificação
 * usam {@code @TransactionalEventListener} e só rodam depois do commit.
 */
@Component
@RequiredArgsConstructor
class PublicadorDeEventos {

    private final ApplicationEventPublisher publisher;

    void publicar(Ocorrencia... ocorrencias) {
        for (Ocorrencia o : ocorrencias) {
            o.extrairEventos().forEach(publisher::publishEvent);
        }
    }
}
