package com.zelo.ocorrencia.infra;

import com.zelo.ocorrencia.domain.StatusOcorrencia;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Consultas de leitura e atualizações atômicas em SQL nativo (PostGIS, agregações, contadores).
 * Os contadores são atualizados direto no banco para não gerar conflito de versão entre curtidas simultâneas.
 */
@Repository
@RequiredArgsConstructor
public class ConsultasDeOcorrencia {

    private final JdbcClient jdbc;

    public record MarcadorDoMapa(UUID id, double latitude, double longitude, StatusOcorrencia status,
                                 UUID categoriaId, int totalCurtidas) {
    }

    public record EstatisticaLinha(UUID categoriaId, long total, long abertas, long emAndamento, long resolvidas,
                                   long arquivadas, Double tempoMedioResolucaoHoras) {
    }

    public record DadosDePrioridade(UUID id, int totalCurtidas, Instant criadaEm) {
    }

    /** RNF02: só os marcadores dentro da área visível, com limite de itens (busca {@code limite + 1} para saber se truncou). */
    public List<MarcadorDoMapa> marcadoresNaArea(double minLongitude, double minLatitude, double maxLongitude,
                                                 double maxLatitude, Collection<StatusOcorrencia> status,
                                                 Collection<UUID> categorias, Instant de, Instant ate, int limite) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, o.latitude, o.longitude, o.status, o.categoria_id, o.total_curtidas
                FROM ocorrencia o
                WHERE o.removida_em IS NULL
                  AND o.ponto && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)
                  AND o.status IN (:status)
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("minLng", minLongitude);
        params.put("minLat", minLatitude);
        params.put("maxLng", maxLongitude);
        params.put("maxLat", maxLatitude);
        params.put("status", status.stream().map(Enum::name).toList());
        if (categorias != null && !categorias.isEmpty()) {
            sql.append(" AND o.categoria_id IN (:categorias)");
            params.put("categorias", categorias);
        }
        if (de != null) {
            sql.append(" AND o.criada_em >= :de");
            params.put("de", Timestamp.from(de));
        }
        if (ate != null) {
            sql.append(" AND o.criada_em < :ate");
            params.put("ate", Timestamp.from(ate));
        }
        sql.append(" ORDER BY o.prioridade DESC, o.criada_em DESC LIMIT :limite");
        params.put("limite", limite + 1);
        return jdbc.sql(sql.toString()).params(params)
                .query((rs, i) -> new MarcadorDoMapa(rs.getObject("id", UUID.class), rs.getDouble("latitude"),
                        rs.getDouble("longitude"), StatusOcorrencia.valueOf(rs.getString("status")),
                        rs.getObject("categoria_id", UUID.class), rs.getInt("total_curtidas")))
                .list();
    }

    /** RF22: totais por categoria na cidade; a linha com {@code categoriaId} nulo é o total geral. */
    public List<EstatisticaLinha> estatisticas(Integer cidadeId, Instant de, Instant ate) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.categoria_id,
                       count(*) AS total,
                       count(*) FILTER (WHERE o.status IN ('EM_ANALISE', 'ABERTA')) AS abertas,
                       count(*) FILTER (WHERE o.status = 'EM_ANDAMENTO') AS em_andamento,
                       count(*) FILTER (WHERE o.status = 'RESOLVIDA') AS resolvidas,
                       count(*) FILTER (WHERE o.status = 'ARQUIVADA') AS arquivadas,
                       (avg(EXTRACT(EPOCH FROM (o.resolvida_em - o.criada_em)) / 3600.0)
                           FILTER (WHERE o.status = 'RESOLVIDA' AND o.resolvida_em IS NOT NULL))::double precision
                           AS tempo_medio_horas
                FROM ocorrencia o
                WHERE o.removida_em IS NULL
                  AND o.cidade_id = :cidadeId
                  AND o.status NOT IN ('REJEITADA', 'DUPLICADA')
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("cidadeId", cidadeId);
        if (de != null) {
            sql.append(" AND o.criada_em >= :de");
            params.put("de", Timestamp.from(de));
        }
        if (ate != null) {
            sql.append(" AND o.criada_em < :ate");
            params.put("ate", Timestamp.from(ate));
        }
        sql.append(" GROUP BY GROUPING SETS ((o.categoria_id), ()) ORDER BY o.categoria_id NULLS FIRST");
        return jdbc.sql(sql.toString()).params(params)
                .query((rs, i) -> new EstatisticaLinha(rs.getObject("categoria_id", UUID.class), rs.getLong("total"),
                        rs.getLong("abertas"), rs.getLong("em_andamento"), rs.getLong("resolvidas"),
                        rs.getLong("arquivadas"), tempoMedio(rs.getDouble("tempo_medio_horas"), rs.wasNull())))
                .list();
    }

    private static Double tempoMedio(double valor, boolean nulo) {
        return nulo ? null : valor;
    }

    /** Autor e quem curtiu: interessados nas mudanças de status (RF17). */
    public Set<UUID> interessados(UUID ocorrenciaId) {
        return new LinkedHashSet<>(jdbc.sql("""
                        SELECT autor_id AS usuario_id FROM ocorrencia WHERE id = :id AND autor_id IS NOT NULL
                        UNION
                        SELECT usuario_id FROM curtida WHERE ocorrencia_id = :id
                        """)
                .param("id", ocorrenciaId)
                .query((rs, i) -> rs.getObject("usuario_id", UUID.class))
                .list());
    }

    // ------------------------------------------------------------------ curtidas

    /** @return true se a curtida foi registrada; false se o usuário já tinha curtido */
    public boolean inserirCurtida(UUID ocorrenciaId, UUID usuarioId, Instant agora) {
        return jdbc.sql("""
                        INSERT INTO curtida (ocorrencia_id, usuario_id, criada_em) VALUES (:o, :u, :agora)
                        ON CONFLICT DO NOTHING""")
                .param("o", ocorrenciaId).param("u", usuarioId).param("agora", Timestamp.from(agora))
                .update() == 1;
    }

    /** @return true se havia curtida para remover */
    public boolean removerCurtida(UUID ocorrenciaId, UUID usuarioId) {
        return jdbc.sql("DELETE FROM curtida WHERE ocorrencia_id = :o AND usuario_id = :u")
                .param("o", ocorrenciaId).param("u", usuarioId)
                .update() == 1;
    }

    public boolean curtiu(UUID ocorrenciaId, UUID usuarioId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM curtida WHERE ocorrencia_id = :o AND usuario_id = :u)")
                .param("o", ocorrenciaId).param("u", usuarioId)
                .query(Boolean.class).single();
    }

    /** Mesclagem: curtidas da duplicada e o autor dela passam a apoiar a principal (sem duplicar, sem o autor da principal). */
    public void migrarApoios(UUID duplicadaId, UUID principalId, UUID autorDuplicada, UUID autorPrincipal, Instant agora) {
        jdbc.sql("""
                        INSERT INTO curtida (ocorrencia_id, usuario_id, criada_em)
                        SELECT :principal, c.usuario_id, c.criada_em FROM curtida c
                        WHERE c.ocorrencia_id = :duplicada
                          AND (CAST(:autorPrincipal AS uuid) IS NULL OR c.usuario_id <> CAST(:autorPrincipal AS uuid))
                        ON CONFLICT DO NOTHING""")
                .param("principal", principalId).param("duplicada", duplicadaId).param("autorPrincipal", autorPrincipal)
                .update();
        if (autorDuplicada != null && !autorDuplicada.equals(autorPrincipal)) {
            inserirCurtida(principalId, autorDuplicada, agora);
        }
    }

    /** Recalcula o total de curtidas e registra atividade (adia o arquivamento). Retorna o novo total. */
    public int sincronizarCurtidas(UUID ocorrenciaId, Instant agora) {
        return jdbc.sql("""
                        UPDATE ocorrencia
                        SET total_curtidas = (SELECT count(*) FROM curtida WHERE ocorrencia_id = :id),
                            ultima_atividade_em = :agora, aviso_arquivamento_em = NULL
                        WHERE id = :id
                        RETURNING total_curtidas""")
                .param("id", ocorrenciaId).param("agora", Timestamp.from(agora))
                .query(Integer.class).single();
    }

    /** Comentários também contam como atividade da ocorrência. */
    public void registrarAtividade(UUID ocorrenciaId, Instant agora) {
        jdbc.sql("UPDATE ocorrencia SET ultima_atividade_em = :agora, aviso_arquivamento_em = NULL WHERE id = :id")
                .param("id", ocorrenciaId).param("agora", Timestamp.from(agora))
                .update();
    }

    // ------------------------------------------------------------------ prioridade

    public List<DadosDePrioridade> dadosDePrioridadeDosAtivos(UUID aposId, int tamanho) {
        return jdbc.sql("""
                        SELECT id, total_curtidas, criada_em FROM ocorrencia
                        WHERE removida_em IS NULL AND status IN ('EM_ANALISE', 'ABERTA', 'EM_ANDAMENTO')
                          AND (CAST(:apos AS uuid) IS NULL OR id > CAST(:apos AS uuid))
                        ORDER BY id LIMIT :tamanho""")
                .param("apos", aposId).param("tamanho", tamanho)
                .query((rs, i) -> new DadosDePrioridade(rs.getObject("id", UUID.class), rs.getInt("total_curtidas"),
                        rs.getTimestamp("criada_em").toInstant()))
                .list();
    }

    public DadosDePrioridade dadosDePrioridade(UUID id) {
        return jdbc.sql("SELECT id, total_curtidas, criada_em FROM ocorrencia WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> new DadosDePrioridade(rs.getObject("id", UUID.class), rs.getInt("total_curtidas"),
                        rs.getTimestamp("criada_em").toInstant()))
                .single();
    }

    public void atualizarPrioridade(UUID id, int prioridade) {
        jdbc.sql("UPDATE ocorrencia SET prioridade = :p WHERE id = :id AND prioridade <> :p")
                .param("p", prioridade).param("id", id)
                .update();
    }
}
