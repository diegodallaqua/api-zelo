package com.zelo.moderacao.infra;

import com.zelo.moderacao.domain.Denuncia;
import com.zelo.moderacao.domain.TipoConteudo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Denúncias pendentes agrupadas por conteúdo, mais denunciados primeiro. */
@Repository
@RequiredArgsConstructor
public class FilaDeModeracao {

    private final JdbcClient jdbc;

    public record ItemDaFila(TipoConteudo tipoConteudo, UUID ocorrenciaId, UUID comentarioId, Integer cidadeId,
                             long totalDenuncias, List<Denuncia.Motivo> motivos, Instant primeiraEm,
                             Instant ultimaEm) {
    }

    private static final String FILTRO = """
            FROM denuncia d
            WHERE d.status = 'PENDENTE'
              AND (CAST(:cidade AS integer) IS NULL OR d.cidade_id = CAST(:cidade AS integer))
            """;

    public List<ItemDaFila> pendentes(Integer cidadeId, int pagina, int tamanho) {
        return jdbc.sql("""
                        SELECT d.tipo_conteudo_id, d.ocorrencia_id, d.comentario_id, d.cidade_id,
                               count(*) AS total, array_agg(DISTINCT d.motivo) AS motivos,
                               min(d.criada_em) AS primeira, max(d.criada_em) AS ultima
                        """ + FILTRO + """
                        GROUP BY d.tipo_conteudo_id, d.ocorrencia_id, d.comentario_id, d.cidade_id
                        ORDER BY total DESC, primeira ASC
                        LIMIT :limite OFFSET :deslocamento""")
                .param("cidade", cidadeId)
                .param("limite", tamanho)
                .param("deslocamento", (long) pagina * tamanho)
                .query((rs, i) -> new ItemDaFila(TipoConteudo.deId(rs.getInt("tipo_conteudo_id")),
                        rs.getObject("ocorrencia_id", UUID.class), rs.getObject("comentario_id", UUID.class),
                        rs.getInt("cidade_id"), rs.getLong("total"), motivos(rs.getArray("motivos")),
                        rs.getTimestamp("primeira").toInstant(), rs.getTimestamp("ultima").toInstant()))
                .list();
    }

    public long contarConteudosPendentes(Integer cidadeId) {
        return jdbc.sql("SELECT count(DISTINCT (d.ocorrencia_id, d.comentario_id)) " + FILTRO)
                .param("cidade", cidadeId)
                .query(Long.class).single();
    }

    private static List<Denuncia.Motivo> motivos(Array array) throws SQLException {
        return Arrays.stream((Object[]) array.getArray()).map(m -> Denuncia.Motivo.valueOf(m.toString())).toList();
    }
}
