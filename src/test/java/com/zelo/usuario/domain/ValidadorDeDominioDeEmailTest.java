package com.zelo.usuario.domain;

import com.zelo.compartilhado.erro.RegraDeNegocioException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorDeDominioDeEmailTest {

    private final ValidadorDeDominioDeEmail validador = new ValidadorDeDominioDeEmail(
            List.of("gmail.com", " Yahoo.com.br ", "hotmail.com", "outlook.com"));

    @ParameterizedTest
    @ValueSource(strings = {"ana@gmail.com", "ANA@GMAIL.COM", "joao@yahoo.com.br", "x@hotmail.com", "y@outlook.com"})
    void aceitaDominiosPermitidos(String email) {
        assertThat(validador.permitido(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@empresa.com", "ana@gmail.com.evil.io", "ana@sub.gmail.com", "semarroba", "ana@", ""})
    void recusaOsDemais(String email) {
        assertThat(validador.permitido(email)).isFalse();
    }

    @Test
    void mensagemListaOsDominios() {
        assertThatThrownBy(() -> validador.validar("ana@empresa.com"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("gmail.com")
                .extracting("codigo").isEqualTo("DOMINIO_DE_EMAIL_NAO_PERMITIDO");
    }
}
