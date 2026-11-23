package com.zelo.compartilhado.seguranca;

import java.util.UUID;

/**
 * Principal extraído do JWT. Use com {@code @AuthenticationPrincipal UsuarioAutenticado usuario};
 * em rotas públicas o valor é {@code null} quando não há token.
 */
public record UsuarioAutenticado(UUID id, Perfil perfil) {
}
