package com.zelo.ocorrencia.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Regra 3.3: encontra ocorrências ativas semelhantes perto do ponto. Isolada atrás de interface porque a
 * estratégia tende a mudar (raio, categorias afins, similaridade de texto).
 */
public interface DetectorDeDuplicidade {

    List<Semelhante> buscar(double latitude, double longitude, UUID categoriaId);

    record Semelhante(UUID id, double latitude, double longitude, double distanciaMetros, StatusOcorrencia status,
                      int totalCurtidas, String descricao, Instant criadaEm) {
    }
}
