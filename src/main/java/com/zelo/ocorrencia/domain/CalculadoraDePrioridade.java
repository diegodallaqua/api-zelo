package com.zelo.ocorrencia.domain;

import java.time.Instant;

/** Prioridade usada para ordenar o painel do gestor. Estratégia trocável. */
public interface CalculadoraDePrioridade {

    int calcular(int totalCurtidas, Instant criadaEm, Instant agora);
}
