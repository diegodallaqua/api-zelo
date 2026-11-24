package com.zelo.ocorrencia.domain;

import java.time.Duration;
import java.time.Instant;

/** Prioridade = curtidas + dias desde a criação. */
public class PrioridadePorCurtidasEDiasEmAberto implements CalculadoraDePrioridade {

    @Override
    public int calcular(int totalCurtidas, Instant criadaEm, Instant agora) {
        long dias = Math.max(0, Duration.between(criadaEm, agora).toDays());
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, totalCurtidas) + dias);
    }
}
