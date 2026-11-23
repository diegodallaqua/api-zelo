package com.zelo.notificacao.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Ocorrências que o usuário silenciou (regra 3.6). */
@Repository
@RequiredArgsConstructor
public class SilenciamentoRepository {

    private final JdbcClient jdbc;

    public void silenciar(UUID usuarioId, UUID ocorrenciaId, Instant agora) {
        jdbc.sql("""
                        INSERT INTO ocorrencia_silenciada (usuario_id, ocorrencia_id, criada_em) VALUES (:u, :o, :agora)
                        ON CONFLICT DO NOTHING""")
                .param("u", usuarioId).param("o", ocorrenciaId).param("agora", Timestamp.from(agora))
                .update();
    }

    public void reativar(UUID usuarioId, UUID ocorrenciaId) {
        jdbc.sql("DELETE FROM ocorrencia_silenciada WHERE usuario_id = :u AND ocorrencia_id = :o")
                .param("u", usuarioId).param("o", ocorrenciaId)
                .update();
    }

    public List<UUID> silenciadasPor(UUID usuarioId) {
        return jdbc.sql("SELECT ocorrencia_id FROM ocorrencia_silenciada WHERE usuario_id = :u ORDER BY criada_em DESC")
                .param("u", usuarioId)
                .query(UUID.class).list();
    }

    /** Usuários que silenciaram a ocorrência. */
    public Set<UUID> quemSilenciou(UUID ocorrenciaId) {
        return new HashSet<>(jdbc.sql("SELECT usuario_id FROM ocorrencia_silenciada WHERE ocorrencia_id = :o")
                .param("o", ocorrenciaId)
                .query(UUID.class).list());
    }

    public void excluirDoUsuario(UUID usuarioId) {
        jdbc.sql("DELETE FROM ocorrencia_silenciada WHERE usuario_id = :u").param("u", usuarioId).update();
    }
}
