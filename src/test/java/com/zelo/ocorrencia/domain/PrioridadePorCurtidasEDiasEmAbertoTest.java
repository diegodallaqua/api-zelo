package com.zelo.ocorrencia.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PrioridadePorCurtidasEDiasEmAbertoTest {

    private final CalculadoraDePrioridade calculadora = new PrioridadePorCurtidasEDiasEmAberto();
    private final Instant criada = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void somaCurtidasEDiasCompletos() {
        assertThat(calculadora.calcular(7, criada, criada.plus(Duration.ofDays(3)).plusSeconds(3600))).isEqualTo(10);
    }

    @Test
    void recemCriadaValeAsCurtidas() {
        assertThat(calculadora.calcular(0, criada, criada)).isZero();
        assertThat(calculadora.calcular(4, criada, criada.plusSeconds(60))).isEqualTo(4);
    }

    @Test
    void naoFicaNegativa() {
        assertThat(calculadora.calcular(-5, criada, criada.minusSeconds(3600))).isZero();
    }
}
