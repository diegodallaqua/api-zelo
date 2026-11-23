package com.zelo.usuario.domain;

import com.zelo.compartilhado.erro.RegraDeNegocioException;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Regra 3.1: contas de cidadão só com e-mail de provedores permitidos (lista em
 * {@code zelo.cadastro.dominios-email-permitidos}), para reduzir spam e trotes.
 */
public class ValidadorDeDominioDeEmail {

    private final Set<String> dominios;

    public ValidadorDeDominioDeEmail(Collection<String> dominios) {
        this.dominios = dominios.stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public boolean permitido(String email) {
        if (email == null) {
            return false;
        }
        int arroba = email.lastIndexOf('@');
        if (arroba < 0 || arroba == email.length() - 1) {
            return false;
        }
        return dominios.contains(email.substring(arroba + 1).trim().toLowerCase(Locale.ROOT));
    }

    public void validar(String email) {
        if (!permitido(email)) {
            throw new RegraDeNegocioException("DOMINIO_DE_EMAIL_NAO_PERMITIDO",
                    "O cadastro só é permitido com e-mail dos domínios: " + String.join(", ", dominios) + ".");
        }
    }
}
