package com.zelo.compartilhado.seguranca;

import java.util.Objects;
import java.util.UUID;

/**
 * Quem executa uma ação, com o escopo de atuação carregado do banco no momento da requisição.
 *
 * @param cidadeId cidade do endereço do usuário; para moderadores e gestores, é a cidade em que atuam
 */
public record Ator(UUID id, Perfil perfil, Integer cidadeId) {

    public Ator {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(perfil, "perfil");
    }

    public static Ator cidadao(UUID id) {
        return new Ator(id, Perfil.CIDADAO, null);
    }

    public boolean isAdmin() {
        return perfil == Perfil.ADMIN;
    }

    public boolean isEquipe() {
        return perfil.isEquipe();
    }

    /** Admin atua em todas as cidades; moderador e gestor apenas na cidade do próprio endereço. */
    public boolean atuaNaCidade(Integer cidade) {
        return isAdmin() || (perfil.atuaPorCidade() && cidadeId != null && cidadeId.equals(cidade));
    }

    /** Moderador, gestor ou admin com atuação na cidade da ocorrência. */
    public boolean podeGerir(Integer cidade) {
        return atuaNaCidade(cidade);
    }
}
