package com.zelo.compartilhado.seguranca;

import java.util.UUID;

/**
 * Carrega o {@link Ator} (perfil e escopo atuais) do usuário autenticado. Implementado pelo módulo
 * de usuários; os demais módulos dependem só desta interface.
 */
public interface ProvedorDeAtor {

    /** Lança {@code NaoAutenticadoException} se a conta não existir mais ou tiver sido excluída. */
    Ator obter(UUID usuarioId);

    /**
     * Para ações de participação (criar, curtir, comentar, denunciar): além de {@link #obter},
     * lança {@code AcessoNegadoException} se o usuário estiver suspenso (regra 3.5).
     */
    Ator obterParticipante(UUID usuarioId);
}
