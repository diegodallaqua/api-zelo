package com.zelo;

import com.zelo.compartilhado.seguranca.Perfil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cadastro, login, sessões e conta do titular, de ponta a ponta com o banco. */
class AutenticacaoIT extends TesteDeIntegracao {

    @Nested
    class Cadastro {

        @Test
        void cidadaoCadastradoTemEnderecoEConsentimentos() throws Exception {
            Conta ana = cidadao();

            executar(get("/api/v1/me", ana.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.perfil").value("CIDADAO"))
                    .andExpect(jsonPath("$.endereco.cep").value("01001000"))
                    .andExpect(jsonPath("$.endereco.cidade.nome").value("São Paulo"))
                    .andExpect(jsonPath("$.endereco.cidade.estado").value("São Paulo"));
            executar(get("/api/v1/me/consentimentos", ana.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void dominioDeEmailNaoPermitido() throws Exception {
            executar(post("/api/v1/auth/cadastro", null, cadastro("ana@empresa.com.br", endereco(SAO_PAULO), true)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("DOMINIO_DE_EMAIL_NAO_PERMITIDO"));
        }

        @Test
        void enderecoEhObrigatorio() throws Exception {
            executar(post("/api/v1/auth/cadastro", null, cadastro(emailUnico("ana", "gmail.com"), "null", true)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
        }

        @Test
        void cidadeDoEnderecoPrecisaExistir() throws Exception {
            executar(post("/api/v1/auth/cadastro", null, cadastro(emailUnico("ana", "gmail.com"), endereco(9999999), true)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("CIDADE_INVALIDA"));
        }

        @Test
        void semAceiteNaoCriaConta() throws Exception {
            String email = emailUnico("ana", "gmail.com");
            executar(post("/api/v1/auth/cadastro", null, cadastro(email, endereco(SAO_PAULO), false)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("CONSENTIMENTO_OBRIGATORIO"));
            assertThat(contar("SELECT count(*) FROM usuario WHERE email = ?", email)).isZero();
        }

        @Test
        void emailRepetidoIgnorandoMaiusculas() throws Exception {
            String email = emailUnico("ana", "gmail.com");
            executar(post("/api/v1/auth/cadastro", null, cadastro(email, endereco(SAO_PAULO), true)))
                    .andExpect(status().isCreated());

            executar(post("/api/v1/auth/cadastro", null, cadastro(email.toUpperCase(), endereco(SAO_PAULO), true)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"));
        }

        private static String cadastro(String email, String endereco, boolean aceite) {
            return """
                    {"nome": "Ana", "email": "%s", "senha": "%s", "endereco": %s,
                     "aceiteTermosDeUso": %b, "aceitePoliticaDePrivacidade": true}"""
                    .formatted(email, SENHA, endereco, aceite);
        }
    }

    @Nested
    class Sessoes {

        @Test
        void senhaErrada() throws Exception {
            executar(post("/api/v1/auth/login", null, """
                    {"email": "ninguem@gmail.com", "senha": "Senha123"}"""))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
        }

        @Test
        @DisplayName("renovação gira o refresh token; reusar um token antigo encerra todas as sessões")
        void rotacaoEDeteccaoDeReuso() throws Exception {
            Conta ana = cidadao();

            String novo = ler(executar(post("/api/v1/auth/renovacao", null, refresh(ana.refreshToken())))
                    .andExpect(status().isOk()), "$.refreshToken");
            assertThat(novo).isNotEqualTo(ana.refreshToken());

            executar(post("/api/v1/auth/renovacao", null, refresh(ana.refreshToken())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("REFRESH_TOKEN_REUTILIZADO"));
            executar(post("/api/v1/auth/renovacao", null, refresh(novo)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("REFRESH_TOKEN_REUTILIZADO"));
            assertThat(contar("SELECT count(*) FROM refresh_token WHERE usuario_id = ? AND revogado_em IS NULL",
                    ana.id())).isZero();
        }

        @Test
        void tokenDesconhecido() throws Exception {
            executar(post("/api/v1/auth/renovacao", null, refresh("inventado")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("REFRESH_TOKEN_INVALIDO"));
        }

        @Test
        void logoutInvalidaORefreshToken() throws Exception {
            Conta ana = cidadao();

            executar(post("/api/v1/auth/logout", null, refresh(ana.refreshToken()))).andExpect(status().isNoContent());

            executar(post("/api/v1/auth/renovacao", null, refresh(ana.refreshToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void trocaDeSenhaEncerraAsSessoes() throws Exception {
            Conta ana = cidadao();
            String email = ler(executar(get("/api/v1/me", ana.token())), "$.email");

            executar(put("/api/v1/me/senha", ana.token(), """
                    {"senhaAtual": "%s", "novaSenha": "NovaSenha9"}""".formatted(SENHA)))
                    .andExpect(status().is2xxSuccessful());

            executar(post("/api/v1/auth/renovacao", null, refresh(ana.refreshToken())))
                    .andExpect(status().isUnauthorized());
            executar(post("/api/v1/auth/login", null, """
                    {"email": "%s", "senha": "%s"}""".formatted(email, SENHA)))
                    .andExpect(status().isUnauthorized());
            login(email, "NovaSenha9");
        }

        private static String refresh(String token) {
            return """
                    {"refreshToken": "%s"}""".formatted(token);
        }
    }

    @Nested
    class Endereco {

        @Test
        void cidadaoMudaOProprioEndereco() throws Exception {
            Conta ana = cidadao();

            executar(put("/api/v1/me/endereco", ana.token(), endereco(RIO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.endereco.cidade.id").value(RIO));
        }

        @Test
        void gestorNaoMudaOProprioEndereco() throws Exception {
            Conta gestor = membroDaEquipe(Perfil.GESTOR, SAO_PAULO);

            executar(put("/api/v1/me/endereco", gestor.token(), endereco(RIO)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ENDERECO_DEFINE_ATUACAO"));
        }

        @Test
        @DisplayName("administrador cria gestor com endereço; a cidade do endereço é a área de atuação")
        void administradorCriaEAlteraAEquipe() throws Exception {
            Conta admin = membroDaEquipe(Perfil.ADMIN, SAO_PAULO);
            String email = emailUnico("gestor", "prefeitura.rj.gov.br");

            executar(post("/api/v1/admin/usuarios", admin.token(), """
                    {"nome": "Gestor", "email": "%s", "senha": "%s", "perfil": "GESTOR"}""".formatted(email, SENHA)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
            String id = ler(executar(post("/api/v1/admin/usuarios", admin.token(), """
                    {"nome": "Gestor", "email": "%s", "senha": "%s", "perfil": "GESTOR", "endereco": %s}"""
                    .formatted(email, SENHA, endereco(RIO)))).andExpect(status().isCreated()), "$.id");

            executar(put("/api/v1/admin/usuarios/" + id + "/perfil", admin.token(), """
                    {"perfil": "MODERADOR"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.perfil").value("MODERADOR"));
            executar(get("/api/v1/admin/usuarios?cidadeId=" + RIO, admin.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItens").value(1))
                    .andExpect(jsonPath("$.itens[0].id").value(id));
            assertThat(contar("SELECT count(*) FROM registro_auditoria WHERE entidade_id = ?", id)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("exclusão de conta: anonimiza, apaga o endereço sem dono e encerra as sessões (LGPD)")
    void exclusaoDeConta() throws Exception {
        Conta ana = cidadao();
        UUID enderecoId = jdbc.queryForObject("SELECT endereco_id FROM usuario WHERE id = ?", UUID.class, ana.id());

        executar(post("/api/v1/me/exclusao", ana.token(), """
                {"senha": "errada"}""")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SENHA_INCORRETA"));
        executar(post("/api/v1/me/exclusao", ana.token(), """
                {"senha": "%s"}""".formatted(SENHA))).andExpect(status().isNoContent());

        Map<String, Object> usuario = linha("SELECT nome, email, senha_hash, endereco_id, excluido_em FROM usuario WHERE id = ?",
                ana.id());
        assertThat(usuario.get("nome")).isEqualTo("Usuário removido");
        assertThat((String) usuario.get("email")).endsWith("@zelo.invalid");
        assertThat(usuario.get("senha_hash")).isNull();
        assertThat(usuario.get("endereco_id")).isNull();
        assertThat(usuario.get("excluido_em")).isNotNull();
        assertThat(contar("SELECT count(*) FROM endereco WHERE id = ?", enderecoId)).isZero();
        assertThat(contar("SELECT count(*) FROM refresh_token WHERE usuario_id = ?", ana.id())).isZero();
    }
}
