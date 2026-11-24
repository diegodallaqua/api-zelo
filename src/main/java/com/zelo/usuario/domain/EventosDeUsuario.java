package com.zelo.usuario.domain;

import java.time.Instant;
import java.util.UUID;

/** Eventos publicados pelo módulo de usuários. */
public final class EventosDeUsuario {

    private EventosDeUsuario() {
    }

    /**
     * Conta excluída pelo titular. Publicado de forma síncrona, dentro da transação: os módulos que
     * guardam dados do usuário devem anonimizá-los ou apagá-los no mesmo commit.
     */
    public record ContaExcluida(UUID usuarioId) {
    }

    /** @param ate nulo quando permanente */
    public record UsuarioSuspenso(UUID usuarioId, Instant ate, boolean permanente, String motivo) {
    }
}
