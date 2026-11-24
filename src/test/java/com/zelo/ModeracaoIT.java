package com.zelo;

import com.zelo.compartilhado.seguranca.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Denúncias, fila e decisões da moderação, suspensão de usuários e seus efeitos, de ponta a ponta. */
class ModeracaoIT extends TesteDeIntegracao {

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

    @Nested
    @DisplayName("denúncia: exatamente um entre ocorrenciaId e comentarioId")
    class Denuncias {

        @Test
        void semAlvoOuComOsDois() throws Exception {
            executar(post("/api/v1/denuncias", denunciante.token(), """
                    {"motivo": "SPAM"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("ALVO_INVALIDO"));
            executar(post("/api/v1/denuncias", denunciante.token(), """
                    {"ocorrenciaId": "%s", "comentarioId": "%s", "motivo": "SPAM"}""".formatted(ocorrenciaId, comentarioId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("ALVO_INVALIDO"));
            assertThat(contar("SELECT count(*) FROM denuncia")).isZero();
        }

        @Test
        void denunciaDeComentarioGuardaSoAChaveDoComentario() throws Exception {
            executar(post("/api/v1/denuncias", denunciante.token(), """
                    {"comentarioId": "%s", "motivo": "OFENSA"}""".formatted(comentarioId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tipoConteudo").value("COMENTARIO"))
                    .andExpect(jsonPath("$.comentarioId").value(comentarioId.toString()))
                    .andExpect(jsonPath("$.ocorrenciaId").value(nullValue()));

            assertThat(linha("SELECT tipo_conteudo_id, ocorrencia_id, cidade_id FROM denuncia"))
                    .containsEntry("tipo_conteudo_id", 2)
                    .containsEntry("cidade_id", SAO_PAULO)
                    .containsEntry("ocorrencia_id", null);
        }

        @Test
        void autorNaoDenunciaOProprioConteudo() throws Exception {
            executar(post("/api/v1/denuncias", autor.token(), """
                    {"ocorrenciaId": "%s", "motivo": "SPAM"}""".formatted(ocorrenciaId)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DENUNCIA_PROPRIA"));
        }

        @Test
        void putTrocaOAlvoESoODenuncianteAltera() throws Exception {
            String id = denunciar(denunciante, "ocorrenciaId", ocorrenciaId);

            executar(put("/api/v1/denuncias/" + id, autor.token(), """
                    {"ocorrenciaId": "%s", "motivo": "SPAM"}""".formatted(ocorrenciaId)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SOMENTE_DENUNCIANTE"));
            executar(put("/api/v1/denuncias/" + id, denunciante.token(), """
                    {"comentarioId": "%s", "motivo": "LOCAL_ERRADO"}""".formatted(comentarioId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tipoConteudo").value("COMENTARIO"))
                    .andExpect(jsonPath("$.ocorrenciaId").value(nullValue()))
                    .andExpect(jsonPath("$.motivo").value("LOCAL_ERRADO"));
        }
    }

    @Test
    @DisplayName("fila → decisão REMOVER_CONTEUDO: comentário some, denúncia procedente, autor avisado")
    void removerComentarioDenunciado() throws Exception {
        String denuncia = denunciar(denunciante, "comentarioId", comentarioId);

        executar(get("/api/v1/moderacao/denuncias", moderador.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].comentarioId").value(comentarioId.toString()))
                .andExpect(jsonPath("$.itens[0].totalDenuncias").value(1));

        executar(post("/api/v1/moderacao/decisoes", moderador.token(), """
                {"comentarioId": "%s", "acao": "REMOVER_CONTEUDO", "motivo": "Linguagem ofensiva"}"""
                .formatted(comentarioId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoConteudo").value("COMENTARIO"))
                .andExpect(jsonPath("$.ocorrenciaId").value(nullValue()));

        assertThat(linha("SELECT status FROM denuncia WHERE id = ?::uuid", denuncia)).containsEntry("status", "PROCEDENTE");
        executar(get("/api/v1/ocorrencias/" + ocorrenciaId + "/comentarios", null))
                .andExpect(jsonPath("$.totalItens").value(0));
        assertThat(contar("SELECT count(*) FROM notificacao WHERE usuario_id = ? AND tipo = 'CONTEUDO_REMOVIDO'",
                autor.id())).isEqualTo(1);
        executar(get("/api/v1/moderacao/denuncias", moderador.token())).andExpect(jsonPath("$.totalItens").value(0));
        executar(put("/api/v1/denuncias/" + denuncia, denunciante.token(), """
                {"comentarioId": "%s", "motivo": "SPAM"}""".formatted(comentarioId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DENUNCIA_JA_ANALISADA"));
    }

    @Test
    void decisaoExigeExatamenteUmAlvo() throws Exception {
        executar(post("/api/v1/moderacao/decisoes", moderador.token(), """
                {"ocorrenciaId": "%s", "comentarioId": "%s", "acao": "MANTER", "motivo": "x"}"""
                .formatted(ocorrenciaId, comentarioId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ALVO_INVALIDO"));
    }

    @Test
    void moderadorDeOutraCidadeNaoDecide() throws Exception {
        denunciar(denunciante, "ocorrenciaId", ocorrenciaId);

        executar(post("/api/v1/moderacao/decisoes", membroDaEquipe(Perfil.MODERADOR, RIO).token(), """
                {"ocorrenciaId": "%s", "acao": "MANTER", "motivo": "Legítima"}""".formatted(ocorrenciaId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FORA_DA_AREA_DE_ATUACAO"));
    }

    @Test
    @DisplayName("mesclar duplicata: vira DUPLICADA e o autor passa a apoiar a principal")
    void mesclarDuplicata() throws Exception {
        Conta outro = cidadao();
        UUID duplicada = ocorrencia(outro);
        denunciar(denunciante, "ocorrenciaId", duplicada);

        executar(post("/api/v1/moderacao/decisoes", moderador.token(), """
                {"ocorrenciaId": "%s", "acao": "MESCLAR_DUPLICATA", "motivo": "Mesmo buraco",
                 "ocorrenciaPrincipalId": "%s"}""".formatted(duplicada, ocorrenciaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ocorrenciaPrincipalId").value(ocorrenciaId.toString()));

        executar(get("/api/v1/ocorrencias/" + duplicada, null))
                .andExpect(jsonPath("$.status").value("DUPLICADA"))
                .andExpect(jsonPath("$.ocorrenciaPrincipalId").value(ocorrenciaId.toString()));
        executar(get("/api/v1/ocorrencias/" + ocorrenciaId, outro.token()))
                .andExpect(jsonPath("$.totalCurtidas").value(1))
                .andExpect(jsonPath("$.curtidaPorMim").value(true));
    }

    @Nested
    @DisplayName("suspensão direta de usuário")
    class Suspensao {

        @Test
        void suspensoNavegaMasNaoParticipaAteSerReativado() throws Exception {
            executar(post("/api/v1/moderacao/usuarios/" + denunciante.id() + "/suspensao", moderador.token(), """
                    {"ate": "2099-01-01T00:00:00Z", "motivo": "Spam em massa"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.acao").value("SUSPENDER_USUARIO"))
                    .andExpect(jsonPath("$.tipoConteudo").value(nullValue()))
                    .andExpect(jsonPath("$.ocorrenciaId").value(nullValue()))
                    .andExpect(jsonPath("$.comentarioId").value(nullValue()));

            executar(get("/api/v1/me", denunciante.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.suspenso").value(true));
            executar(get("/api/v1/ocorrencias/" + ocorrenciaId, denunciante.token())).andExpect(status().isOk());
            executar(post("/api/v1/ocorrencias/" + ocorrenciaId + "/curtida", denunciante.token(), null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("USUARIO_SUSPENSO"));
            Ponto ponto = pontoLivreEmSaoPaulo();
            registrarOcorrencia(denunciante, BURACO_NA_VIA, ponto, ponto, false)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("USUARIO_SUSPENSO"));
            assertThat(contar("SELECT count(*) FROM notificacao WHERE usuario_id = ? AND tipo = 'CONTA_SUSPENSA'",
                    denunciante.id())).isEqualTo(1);

            executar(post("/api/v1/moderacao/usuarios/" + denunciante.id() + "/reativacao", moderador.token(), """
                    {"motivo": "Recurso aceito"}""")).andExpect(status().isCreated());
            executar(post("/api/v1/ocorrencias/" + ocorrenciaId + "/curtida", denunciante.token(), null))
                    .andExpect(status().isCreated());
            executar(get("/api/v1/moderacao/usuarios/" + denunciante.id() + "/decisoes", moderador.token()))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].acao").value("REATIVAR_USUARIO"))
                    .andExpect(jsonPath("$[1].acao").value("SUSPENDER_USUARIO"));
        }

        @Test
        void moderadorNaoSuspendeAdministrador() throws Exception {
            Conta admin = membroDaEquipe(Perfil.ADMIN, SAO_PAULO);

            executar(post("/api/v1/moderacao/usuarios/" + admin.id() + "/suspensao", moderador.token(), """
                    {"permanente": true, "motivo": "x"}"""))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ALVO_PROTEGIDO"));
            assertThat(contar("SELECT count(*) FROM decisao_moderacao")).isZero();
        }
    }

    private String denunciar(Conta quem, String chave, UUID alvo) throws Exception {
        return ler(executar(post("/api/v1/denuncias", quem.token(), """
                {"%s": "%s", "motivo": "SPAM"}""".formatted(chave, alvo))).andExpect(status().isCreated()), "$.id");
    }
}
