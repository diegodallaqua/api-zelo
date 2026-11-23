package com.zelo.localidade.infra;

import com.zelo.localidade.domain.Cidade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CidadeRepository extends JpaRepository<Cidade, Integer> {

    String COLUNAS = """
            c.id, c.nome, c.estado_id, c.ativo, c.aviso_canal_nao_oficial, c.url_canal_oficial, c.texto_legal,
            c.criado_em, c.atualizado_em""";

    String FILTRO = """
             FROM cidade c
            WHERE (CAST(:estadoId AS integer) IS NULL OR c.estado_id = CAST(:estadoId AS integer))
              AND (CAST(:nome AS varchar) IS NULL
                   OR f_unaccent(lower(c.nome)) LIKE '%' || f_unaccent(lower(CAST(:nome AS varchar))) || '%')""";

    /** Cidade cujo polígono contém o ponto (inclui a divisa). */
    @Query(value = """
            SELECT c.id FROM cidade c
            WHERE ST_Covers(c.limite, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326))
            ORDER BY c.id
            LIMIT 1""", nativeQuery = true)
    Optional<Integer> buscarIdContendoPonto(@Param("latitude") double latitude, @Param("longitude") double longitude);

    /** Busca por nome sem diferenciar acentos e maiúsculas, opcionalmente dentro de um estado. */
    @Query(value = "SELECT " + COLUNAS + FILTRO + " ORDER BY c.nome, c.estado_id",
            countQuery = "SELECT count(*)" + FILTRO,
            nativeQuery = true)
    Page<Cidade> pesquisar(@Param("nome") String nome, @Param("estadoId") Integer estadoId, Pageable pageable);

    @Query(value = """
            SELECT ST_XMin(c.limite) AS "minLongitude", ST_YMin(c.limite) AS "minLatitude",
                   ST_XMax(c.limite) AS "maxLongitude", ST_YMax(c.limite) AS "maxLatitude",
                   ST_Y(ST_PointOnSurface(c.limite)) AS "latitudeCentral",
                   ST_X(ST_PointOnSurface(c.limite)) AS "longitudeCentral"
            FROM cidade c WHERE c.id = :id""", nativeQuery = true)
    Optional<LimitesProjecao> buscarLimites(@Param("id") Integer id);

    interface LimitesProjecao {
        double getMinLongitude();

        double getMinLatitude();

        double getMaxLongitude();

        double getMaxLatitude();

        double getLatitudeCentral();

        double getLongitudeCentral();
    }
}
