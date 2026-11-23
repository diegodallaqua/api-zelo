package com.zelo.notificacao.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Envio por canais externos (push, e-mail). A implementação atual só registra em log; FCM e e-mail
 * entram depois sem mudar as regras de quem é notificado.
 */
public interface Notificador {

    enum Canal { PUSH, EMAIL }

    void enviar(Envio envio);

    record Envio(UUID usuarioId, Set<Canal> canais, String titulo, String mensagem, UUID ocorrenciaId) {
    }
}
