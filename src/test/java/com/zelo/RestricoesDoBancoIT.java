package com.zelo;

import com.zelo.compartilhado.seguranca.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras garantidas pelo próprio banco (constraints, triggers e dados das migrations), testadas com SQL direto. */
class RestricoesDoBancoIT extends TesteDeIntegracao {

    private static final int OCORRENCIA = 1;
    private static final int COMENTARIO = 2;

    private Conta autor;
    private Conta denunciante;
    private Conta moderador;
    private UUID ocorrenciaId;
    private UUID comentarioId;

    @BeforeEach
    void criarConteudo() throws Exception {
        autor = cidadao();
        denunciante = cidadao();
        moderador = membroDaEquipe(Perfil.MODERADOR, SAO_PAULO);
        ocorrenciaId = ocorrencia(autor);
        comentarioId = comentario(autor, ocorrenciaId);
    }

    @Test
    @DisplayName("migrations aplicadas sem falha, inclusive as tabelas de apoio com ids fixos")
    void migrations() {
        assertThat(contar("SELECT count(*) FROM flyway_schema_history WHERE NOT success")).isZero();
        assertThat(contar("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL")).isGreaterThanOrEqualTo(10);
        assertThat(jdbc.queryForList("SELECT id, nome FROM perfil_usuario ORDER BY id")).containsExactly(
                Map.of("id", 1, "nome", "Cidadão"), Map.of("id", 2, "nome", "Moderador"),
                Map.of("id", 3, "nome", "Gestor público"), Map.of("id", 4, "nome", "Administrador"));
        assertThat(jdbc.queryForList("SELECT id, nome FROM tipo_conteudo ORDER BY id")).containsExactly(
                Map.of("id", 1, "nome", "Ocorrência"), Map.of("id", 2, "nome", "Comentário"));
        assertThat(contar("SELECT count(*) FROM categoria WHERE ativa")).isGreaterThanOrEqualTo(7);
    }

    @Nested
    @DisplayName("denúncia aponta para exatamente um conteúdo (ck_denuncia_alvo)")
    class AlvoDaDenuncia {

        @Test
        void semNenhumAlvo() {
            assertThatThrownBy(() -> inserirDenuncia(OCORRENCIA, null, null))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_denuncia_alvo");
        }

        @Test
        void comOsDoisAlvos() {
            assertThatThrownBy(() -> inserirDenuncia(OCORRENCIA, ocorrenciaId, comentarioId))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_denuncia_alvo");
        }

        @Test
        void tipoDiferenteDoAlvo() {
            assertThatThrownBy(() -> inserirDenuncia(COMENTARIO, ocorrenciaId, null))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_denuncia_alvo");
        }

        @Test
        void umAlvoSoEhAceito() {
            assertThatCode(() -> inserirDenuncia(OCORRENCIA, ocorrenciaId, null)).doesNotThrowAnyException();
            assertThatCode(() -> inserirDenuncia(COMENTARIO, null, comentarioId)).doesNotThrowAnyException();
        }

        @Test
        void mesmoUsuarioNaoDenunciaDuasVezes() {
            inserirDenuncia(OCORRENCIA, ocorrenciaId, null);
            assertThatThrownBy(() -> inserirDenuncia(OCORRENCIA, ocorrenciaId, null))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("uk_denuncia_ocorrencia_por_usuario");
        }

        private void inserirDenuncia(int tipo, UUID ocorrencia, UUID comentario) {
            jdbc.update("""
                    INSERT INTO denuncia (id, denunciante_id, tipo_conteudo_id, ocorrencia_id, comentario_id, cidade_id,
                                          motivo, criada_em, atualizada_em)
                    VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, 'SPAM', now(), now())""",
                    denunciante.id(), tipo, ocorrencia, comentario, SAO_PAULO);
        }
    }

    @Nested
    @DisplayName("decisão de moderação: um conteúdo, ou nenhum na suspensão direta (ck_decisao_alvo)")
    class AlvoDaDecisao {

        @Test
        void decisaoSobreConteudoSemAlvo() {
            assertThatThrownBy(() -> inserirDecisao("MANTER", null, null, null))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_decisao_alvo");
        }

        @Test
        void decisaoSobreConteudoComOsDoisAlvos() {
            assertThatThrownBy(() -> inserirDecisao("REMOVER_CONTEUDO", OCORRENCIA, ocorrenciaId, comentarioId))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_decisao_alvo");
        }

        @Test
        void suspensaoDiretaNaoTemConteudo() {
            assertThatThrownBy(() -> inserirDecisao("SUSPENDER_USUARIO", OCORRENCIA, ocorrenciaId, null))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_decisao_alvo");
            assertThatCode(() -> inserirDecisao("SUSPENDER_USUARIO", null, null, null)).doesNotThrowAnyException();
        }

        @Test
        void mesclagemExigeAPrincipal() {
            assertThatThrownBy(() -> inserirDecisao("MESCLAR_DUPLICATA", OCORRENCIA, ocorrenciaId, null))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_decisao_mesclagem");
        }

        @Test
        void decisaoValidaSobreComentario() {
            assertThatCode(() -> inserirDecisao("REMOVER_CONTEUDO", COMENTARIO, null, comentarioId))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("tabelas append-only recusam UPDATE e DELETE")
    class TabelasImutaveis {

        @Test
        void historicoDeStatus() {
            assertThatThrownBy(() -> jdbc.update("UPDATE historico_status SET motivo = 'x' WHERE ocorrencia_id = ?",
                    ocorrenciaId))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("imutáveis");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM historico_status WHERE ocorrencia_id = ?", ocorrenciaId))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("imutáveis");
        }

        @Test
        void decisaoDeModeracao() {
            UUID id = inserirDecisao("REMOVER_CONTEUDO", OCORRENCIA, ocorrenciaId, null);
            assertThatThrownBy(() -> jdbc.update("UPDATE decisao_moderacao SET motivo = 'x' WHERE id = ?", id))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("imutáveis");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM decisao_moderacao WHERE id = ?", id))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("imutáveis");
        }

        @Test
        void registroDeAuditoria() {
            jdbc.update("""
                    INSERT INTO registro_auditoria (papel_ator, acao, entidade, entidade_id, ocorrido_em)
                    VALUES ('SISTEMA', 'TESTE', 'USUARIO', '1', now())""");
            assertThatThrownBy(() -> jdbc.update("UPDATE registro_auditoria SET acao = 'OUTRA'"))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("imutáveis");
        }
    }

    @Nested
    class Usuario {

        @Test
        void perfilPrecisaExistirNaTabelaDePerfis() {
            assertThatThrownBy(() -> jdbc.update("UPDATE usuario SET perfil_id = 9 WHERE id = ?", autor.id()))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("perfil_id");
        }

        @Test
        void contaAtivaPrecisaDeEndereco() {
            assertThatThrownBy(() -> jdbc.update("UPDATE usuario SET endereco_id = NULL WHERE id = ?", autor.id()))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_usuario_endereco_obrigatorio");
        }

        @Test
        void contaExcluidaPodeFicarSemEndereco() {
            assertThatCode(() -> jdbc.update(
                    "UPDATE usuario SET endereco_id = NULL, excluido_em = now() WHERE id = ?", autor.id()))
                    .doesNotThrowAnyException();
        }

        @Test
        void cepTemOitoDigitos() {
            assertThatThrownBy(() -> jdbc.update("UPDATE endereco SET cep = '123'"))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_endereco_cep");
        }
    }

    @Test
    void curtidaUnicaPorUsuario() {
        jdbc.update("INSERT INTO curtida (ocorrencia_id, usuario_id, criada_em) VALUES (?, ?, now())",
                ocorrenciaId, denunciante.id());
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO curtida (ocorrencia_id, usuario_id, criada_em) VALUES (?, ?, now())",
                ocorrenciaId, denunciante.id()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void pontoDaOcorrenciaEhGeradoPelasCoordenadas() {
        List<Map<String, Object>> linhas = jdbc.queryForList(
                "SELECT ST_Y(ponto) AS lat, ST_X(ponto) AS lon, latitude, longitude, ST_SRID(ponto) AS srid "
                        + "FROM ocorrencia WHERE id = ?", ocorrenciaId);
        assertThat(linhas).singleElement().satisfies(l -> {
            assertThat(l.get("lat")).isEqualTo(l.get("latitude"));
            assertThat(l.get("lon")).isEqualTo(l.get("longitude"));
            assertThat(l.get("srid")).isEqualTo(4326);
        });
    }

    private UUID inserirDecisao(String acao, Integer tipo, UUID ocorrencia, UUID comentario) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO decisao_moderacao (id, moderador_id, acao, tipo_conteudo_id, ocorrencia_id, comentario_id,
                                               motivo, decidida_em)
                VALUES (?, ?, ?, ?, ?, ?, 'Motivo do teste', now())""",
                id, moderador.id(), acao, tipo, ocorrencia, comentario);
        return id;
    }
}
