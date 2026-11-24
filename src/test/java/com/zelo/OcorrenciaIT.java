package com.zelo;

import com.zelo.compartilhado.seguranca.Perfil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registro e consulta de ocorrências com as validações geográficas do PostGIS e os contadores em SQL. */
class OcorrenciaIT extends TesteDeIntegracao {

    @Nested
    @DisplayName("local da ocorrência (polígonos das cidades)")
    class Localizacao {

        @Test
        void registraNaCidadeQueContemOPonto() throws Exception {
            Ponto ponto = pontoLivreEmSaoPaulo();

            registrarOcorrencia(cidadao(), BURACO_NA_VIA, ponto, ponto, false)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("ABERTA"))
                    .andExpect(jsonPath("$.cidade.id").value(SAO_PAULO))
                    .andExpect(jsonPath("$.cidade.estado").value("São Paulo"))
                    .andExpect(jsonPath("$.minha").value(true))
                    .andExpect(jsonPath("$.fotos.length()").value(1))
                    .andExpect(jsonPath("$.fotos[0].url").value(startsWith("http://localhost/arquivos/ocorrencias/")));
        }

        @Test
        void pontoForaDeQualquerCidade() throws Exception {
            registrarOcorrencia(cidadao(), BURACO_NA_VIA, OCEANO, PRACA_DA_SE, false)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("LOCAL_FORA_DE_CIDADE"));
        }

        @Test
        void dispositivoForaDeQualquerCidade() throws Exception {
            registrarOcorrencia(cidadao(), BURACO_NA_VIA, PRACA_DA_SE, OCEANO, false)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DISPOSITIVO_FORA_DE_CIDADE"));
        }

        @Test
        void dispositivoEmOutraCidade() throws Exception {
            registrarOcorrencia(cidadao(), BURACO_NA_VIA, PRACA_DA_SE, CENTRO_DO_RIO, false)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("CIDADE_DIFERENTE_DO_DISPOSITIVO"));
        }

        @Test
        void cidadeDesativada() throws Exception {
            registrarOcorrencia(cidadao(), BURACO_NA_VIA, CENTRO_DE_CAMPINAS, CENTRO_DE_CAMPINAS, false)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("CIDADE_INATIVA"));
        }

        @Test
        void cidadeDaLocalizacaoDoUsuario() throws Exception {
            executar(get("/api/v1/cidades/localizacao?latitude=%s&longitude=%s"
                    .formatted(CENTRO_DO_RIO.latitude(), CENTRO_DO_RIO.longitude()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(RIO));
            executar(get("/api/v1/cidades/localizacao?latitude=%s&longitude=%s"
                    .formatted(OCEANO.latitude(), OCEANO.longitude()), null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("CIDADE_NAO_ENCONTRADA"));
        }
    }

    @Nested
    @DisplayName("ocorrências semelhantes num raio de 25 m")
    class Duplicidade {

        @Test
        void mesmaCategoriaA10MetrosPedeConfirmacao() throws Exception {
            Ponto base = pontoLivreEmSaoPaulo();
            UUID existente = ocorrencia(cidadao(), BURACO_NA_VIA, base);
            Ponto perto = base.aoNorte(10);

            registrarOcorrencia(cidadao(), BURACO_NA_VIA, perto, perto, false)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("OCORRENCIAS_SEMELHANTES"))
                    .andExpect(jsonPath("$.semelhantes[0].id").value(existente.toString()));
        }

        @Test
        void confirmacaoDoUsuarioRegistraMesmoAssim() throws Exception {
            Ponto base = pontoLivreEmSaoPaulo();
            ocorrencia(cidadao(), BURACO_NA_VIA, base);

            registrarOcorrencia(cidadao(), BURACO_NA_VIA, base.aoNorte(10), base.aoNorte(10), true)
                    .andExpect(status().isCreated());
        }

        @Test
        void foraDoRaioOuOutraCategoriaNaoEhSemelhante() throws Exception {
            Ponto base = pontoLivreEmSaoPaulo();
            ocorrencia(cidadao(), BURACO_NA_VIA, base);
            Ponto a40Metros = base.aoNorte(40);

            registrarOcorrencia(cidadao(), BURACO_NA_VIA, a40Metros, a40Metros, false).andExpect(status().isCreated());
            registrarOcorrencia(cidadao(), ILUMINACAO, base, base, false).andExpect(status().isCreated());
        }
    }

    @Test
    void limiteDeCincoPorDia() throws Exception {
        Conta ana = cidadao();
        for (int i = 0; i < 5; i++) {
            ocorrencia(ana);
        }
        Ponto ponto = pontoLivreEmSaoPaulo();

        registrarOcorrencia(ana, BURACO_NA_VIA, ponto, ponto, false)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LIMITE_DIARIO_DE_OCORRENCIAS"));
    }

    @Test
    void fotoEhObrigatoria() throws Exception {
        Conta ana = cidadao();
        executar(multipart("/api/v1/ocorrencias")
                .param("categoriaId", categoria(BURACO_NA_VIA).toString())
                .param("descricao", "Buraco enorme no meio da rua")
                .param("latitude", "-23.55").param("longitude", "-46.63")
                .param("latitudeDispositivo", "-23.55").param("longitudeDispositivo", "-46.63")
                .header("Authorization", "Bearer " + ana.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("FOTO_OBRIGATORIA"));
    }

    @Nested
    @DisplayName("listagem, mapa e estatísticas")
    class Consultas {

        @Test
        void filtrosPorCidadeECategoria() throws Exception {
            ocorrencia(cidadao(), BURACO_NA_VIA, pontoLivreEmSaoPaulo());
            UUID iluminacao = ocorrencia(cidadao(), ILUMINACAO, pontoLivreEmSaoPaulo());
            ocorrencia(cidadao(RIO), BURACO_NA_VIA, CENTRO_DO_RIO);

            executar(get("/api/v1/ocorrencias?cidadeId=%d&categoriaId=%s".formatted(SAO_PAULO, categoria(ILUMINACAO)), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItens").value(1))
                    .andExpect(jsonPath("$.itens[0].id").value(iluminacao.toString()));
            executar(get("/api/v1/ocorrencias?categoriaId=" + categoria(BURACO_NA_VIA), null))
                    .andExpect(jsonPath("$.totalItens").value(2));
            executar(get("/api/v1/ocorrencias?cidadeId=" + RIO, null))
                    .andExpect(jsonPath("$.totalItens").value(1));
        }

        @Test
        void mapaTrazSoOQueEstaNaArea() throws Exception {
            UUID emSaoPaulo = ocorrencia(cidadao(), BURACO_NA_VIA, pontoLivreEmSaoPaulo());
            ocorrencia(cidadao(RIO), BURACO_NA_VIA, CENTRO_DO_RIO);

            executar(get("/api/v1/ocorrencias/mapa?minLongitude=-46.83&minLatitude=-24.01"
                    + "&maxLongitude=-46.36&maxLatitude=-23.36", null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens.length()").value(1))
                    .andExpect(jsonPath("$.itens[0].id").value(emSaoPaulo.toString()))
                    .andExpect(jsonPath("$.truncado").value(false));
        }

        @Test
        void estatisticasDaCidade() throws Exception {
            ocorrencia(cidadao());
            ocorrencia(cidadao());

            executar(get("/api/v1/estatisticas?cidadeId=" + SAO_PAULO, null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cidade.id").value(SAO_PAULO))
                    .andExpect(jsonPath("$.geral.total").value(2))
                    .andExpect(jsonPath("$.geral.abertas").value(2));
        }
    }

    @Nested
    @DisplayName("curtidas com contador atômico")
    class Curtidas {

        @Test
        void curtirAtualizaContadorEPrioridade() throws Exception {
            UUID id = ocorrencia(cidadao());
            int prioridadeAntes = prioridade(id);
            Conta apoiador = cidadao();

            executar(post("/api/v1/ocorrencias/" + id + "/curtida", apoiador.token(), null))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.totalCurtidas").value(1))
                    .andExpect(jsonPath("$.curtidaPorMim").value(true));
            executar(post("/api/v1/ocorrencias/" + id + "/curtida", apoiador.token(), null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CURTIDA_EXISTENTE"));

            assertThat(contar("SELECT total_curtidas FROM ocorrencia WHERE id = ?", id)).isEqualTo(1);
            assertThat(prioridade(id)).isGreaterThan(prioridadeAntes);
            executar(get("/api/v1/ocorrencias/" + id, apoiador.token()))
                    .andExpect(jsonPath("$.totalCurtidas").value(1))
                    .andExpect(jsonPath("$.curtidaPorMim").value(true));

            executar(delete("/api/v1/ocorrencias/" + id + "/curtida", apoiador.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCurtidas").value(0));
            assertThat(contar("SELECT total_curtidas FROM ocorrencia WHERE id = ?", id)).isZero();
        }

        @Test
        void autorNaoCurteAPropria() throws Exception {
            Conta autor = cidadao();
            UUID id = ocorrencia(autor);

            executar(post("/api/v1/ocorrencias/" + id + "/curtida", autor.token(), null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("CURTIDA_PROPRIA"));
        }

        private int prioridade(UUID id) {
            return (int) contar("SELECT prioridade FROM ocorrencia WHERE id = ?", id);
        }
    }

    @Test
    @DisplayName("ciclo de vida: gestor atende e resolve, autor contesta; autor e apoiadores são notificados")
    void cicloDeVidaComNotificacoes() throws Exception {
        Conta autor = cidadao();
        Conta apoiador = cidadao();
        Conta gestor = membroDaEquipe(Perfil.GESTOR, SAO_PAULO);
        UUID id = ocorrencia(autor);
        executar(post("/api/v1/ocorrencias/" + id + "/curtida", apoiador.token(), null)).andExpect(status().isCreated());

        executar(post("/api/v1/ocorrencias/" + id + "/transicoes", gestor.token(), """
                {"status": "EM_ANDAMENTO", "motivo": "Equipe enviada"}""")).andExpect(status().isOk());
        executar(post("/api/v1/ocorrencias/" + id + "/transicoes", gestor.token(), """
                {"status": "RESOLVIDA"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prazoContestacaoAte").isNotEmpty());
        executar(post("/api/v1/ocorrencias/" + id + "/resolucao/contestacao", apoiador.token(), """
                {"motivo": "Não sou o autor"}""")).andExpect(status().isForbidden());
        executar(post("/api/v1/ocorrencias/" + id + "/resolucao/contestacao", autor.token(), """
                {"motivo": "O buraco continua lá"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.historico[-1].tipo").value("CONTESTACAO"));

        assertThat(contar("SELECT count(*) FROM historico_status WHERE ocorrencia_id = ?", id)).isEqualTo(4);
        assertThat(notificacoes(autor)).as("o autor não é avisado da própria contestação").isEqualTo(2);
        assertThat(notificacoes(apoiador)).isEqualTo(3);
        executar(get("/api/v1/me/notificacoes", apoiador.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].tipo").value("STATUS_ALTERADO"));
    }

    @Test
    void silenciarParaDeNotificar() throws Exception {
        Conta autor = cidadao();
        Conta gestor = membroDaEquipe(Perfil.GESTOR, SAO_PAULO);
        UUID id = ocorrencia(autor);

        executar(put("/api/v1/ocorrencias/" + id + "/silenciamento", autor.token(), null))
                .andExpect(status().is2xxSuccessful());
        executar(post("/api/v1/ocorrencias/" + id + "/transicoes", gestor.token(), """
                {"status": "EM_ANDAMENTO"}""")).andExpect(status().isOk());

        assertThat(notificacoes(autor)).isZero();
    }

    private long notificacoes(Conta conta) {
        return contar("SELECT count(*) FROM notificacao WHERE usuario_id = ? AND tipo = 'STATUS_ALTERADO'", conta.id());
    }
}
