package com.zelo.compartilhado.seguranca;

import java.util.Arrays;

/**
 * Perfis de acesso. O {@code id} é a chave da tabela {@code perfil_usuario} (migração V4), que guarda o nome
 * de exibição. Hierarquia (ver {@link SecurityConfig}): ADMIN herda MODERADOR e GESTOR, que herdam CIDADAO.
 */
public enum Perfil {
    CIDADAO(1),
    MODERADOR(2),
    GESTOR(3),
    ADMIN(4);

    private final int id;

    Perfil(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static Perfil deId(int id) {
        return Arrays.stream(values()).filter(p -> p.id == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil desconhecido: " + id));
    }

    /** Moderadores e gestores atuam na cidade do próprio endereço. */
    public boolean atuaPorCidade() {
        return this == MODERADOR || this == GESTOR;
    }

    public boolean isEquipe() {
        return this != CIDADAO;
    }
}
