package com.zelo.ocorrencia.infra;

import com.zelo.ocorrencia.application.OcorrenciaProperties;
import com.zelo.ocorrencia.domain.DetectorDeDuplicidade;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Ocorrências ativas da mesma categoria num raio em metros (geography do PostGIS). */
@Component
@RequiredArgsConstructor
public class DetectorDeDuplicidadePorRaio implements DetectorDeDuplicidade {

    private static final int MAXIMO_DE_SUGESTOES = 10;

    private final JdbcClient jdbc;
    private final OcorrenciaProperties properties;

    @Override
    public List<Semelhante> buscar(double latitude, double longitude, UUID categoriaId) {
        return jdbc.sql("""
                        WITH alvo AS (SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS g)
                        SELECT o.id, o.latitude, o.longitude, o.status, o.total_curtidas, o.descricao, o.criada_em,
                               ST_Distance(o.ponto::geography, alvo.g) AS distancia
                        FROM ocorrencia o, alvo
                        WHERE o.removida_em IS NULL
                          AND o.categoria_id = :categoria
                          AND o.status IN ('EM_ANALISE', 'ABERTA', 'EM_ANDAMENTO')
                          AND ST_DWithin(o.ponto::geography, alvo.g, :raio)
                        ORDER BY distancia
                        LIMIT :limite""")
                .param("lng", longitude).param("lat", latitude).param("categoria", categoriaId)
                .param("raio", properties.raioDuplicidadeMetros()).param("limite", MAXIMO_DE_SUGESTOES)
                .query((rs, i) -> new Semelhante(rs.getObject("id", UUID.class), rs.getDouble("latitude"),
                        rs.getDouble("longitude"), Math.round(rs.getDouble("distancia") * 10) / 10.0,
                        StatusOcorrencia.valueOf(rs.getString("status")), rs.getInt("total_curtidas"),
                        rs.getString("descricao"), rs.getTimestamp("criada_em").toInstant()))
                .list();
    }
}
