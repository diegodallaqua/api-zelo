package com.zelo;

import com.zelo.compartilhado.seguranca.Perfil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regras de acesso por perfil do SecurityConfig, com tokens JWT reais emitidos pelo login. */
class SegurancaIT extends TesteDeIntegracao {

    @ParameterizedTest(name = "{0} GET {1} -> {2}")
    @DisplayName("acesso às rotas por perfil (ADMIN herda MODERADOR e GESTOR)")
    @CsvSource({
            "ANONIMO,   /api/v1/categorias,                          200",
            "ANONIMO,   /api/v1/estados,                             200",
            "ANONIMO,   /api/v1/cidades/3550308,                     200",
            "ANONIMO,   /api/v1/ocorrencias,                         200",
            "ANONIMO,   /api/v1/estatisticas?cidadeId=3550308,       200",
            "ANONIMO,   /api/v1/me,                                  401",
            "ANONIMO,   /api/v1/me/notificacoes,                     401",
            "ANONIMO,   /api/v1/admin/usuarios,                      401",
            "ANONIMO,   /api/v1/moderacao/denuncias,                 401",
            "ANONIMO,   /api/v1/gestao/ocorrencias,                  401",
            "CIDADAO,   /api/v1/me,                                  200",
            "CIDADAO,   /api/v1/me/ocorrencias,                      200",
            "CIDADAO,   /api/v1/admin/usuarios,                      403",
            "CIDADAO,   /api/v1/admin/auditoria,                     403",
            "CIDADAO,   /api/v1/moderacao/denuncias,                 403",
            "CIDADAO,   /api/v1/gestao/ocorrencias,                  403",
            "MODERADOR, /api/v1/moderacao/denuncias,                 200",
            "MODERADOR, /api/v1/gestao/ocorrencias,                  403",
            "MODERADOR, /api/v1/admin/usuarios,                      403",
            "GESTOR,    /api/v1/gestao/ocorrencias,                  200",
            "GESTOR,    /api/v1/moderacao/denuncias,                 403",
            "GESTOR,    /api/v1/admin/categorias,                    403",
            "ADMIN,     /api/v1/admin/usuarios,                      200",
            "ADMIN,     /api/v1/admin/auditoria,                     200",
            "ADMIN,     /api/v1/admin/categorias,                    200",
            "ADMIN,     /api/v1/moderacao/denuncias,                 200",
            "ADMIN,     /api/v1/gestao/ocorrencias?cidadeId=3550308, 200",
    })
    void acessoPorPerfil(String perfil, String rota, int esperado) throws Exception {
        var resultado = executar(get(rota, token(perfil))).andExpect(status().is(esperado));
        if (esperado == 401) {
            resultado.andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        } else if (esperado == 403) {
            resultado.andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        }
    }

    @Test
    void tokenAdulteradoEhRecusado() throws Exception {
        String token = cidadao().token();
        String adulterado = token.substring(0, token.length() - 4) + "AAAA";

        executar(get("/api/v1/me", adulterado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
        executar(get("/api/v1/me", "nao-e-um-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }

    @Test
    void administradorPrecisaInformarACidadeNoPainelDeGestao() throws Exception {
        executar(get("/api/v1/gestao/ocorrencias", membroDaEquipe(Perfil.ADMIN, SAO_PAULO).token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CIDADE_OBRIGATORIA"));
    }

    @Test
    void gestorNaoConsultaOPainelDeOutraCidade() throws Exception {
        executar(get("/api/v1/gestao/ocorrencias?cidadeId=" + RIO, membroDaEquipe(Perfil.GESTOR, SAO_PAULO).token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FORA_DA_AREA_DE_ATUACAO"));
    }

    @Test
    @DisplayName("transições de status: cidadão barrado na segurança; gestor só na cidade do endereço")
    void transicoesDeStatus() throws Exception {
        Conta autor = cidadao();
        UUID id = ocorrencia(autor);
        String corpo = """
                {"status": "EM_ANDAMENTO", "motivo": "Equipe enviada"}""";

        executar(post("/api/v1/ocorrencias/" + id + "/transicoes", autor.token(), corpo))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        executar(post("/api/v1/ocorrencias/" + id + "/transicoes", membroDaEquipe(Perfil.GESTOR, RIO).token(), corpo))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FORA_DA_AREA_DE_ATUACAO"));
        executar(post("/api/v1/ocorrencias/" + id + "/transicoes", membroDaEquipe(Perfil.GESTOR, SAO_PAULO).token(), corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
        executar(post("/api/v1/ocorrencias/" + id + "/transicoes", membroDaEquipe(Perfil.ADMIN, RIO).token(), """
                {"status": "RESOLVIDA"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDA"));
    }

    @Test
    void rotaInexistente() throws Exception {
        executar(get("/api/v1/nao-existe", cidadao().token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ROTA_NAO_ENCONTRADA"));
        executar(get("/api/v1/nao-existe", null)).andExpect(status().isUnauthorized());
    }

    @Test
    void contaExcluidaPerdeOAcessoMesmoComTokenValido() throws Exception {
        Conta ana = cidadao();
        executar(post("/api/v1/me/exclusao", ana.token(), """
                {"senha": "%s"}""".formatted(SENHA))).andExpect(status().isNoContent());

        executar(get("/api/v1/me", ana.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CONTA_INDISPONIVEL"));
    }

    private String token(String perfil) throws Exception {
        return switch (perfil) {
            case "ANONIMO" -> null;
            case "CIDADAO" -> cidadao().token();
            default -> membroDaEquipe(Perfil.valueOf(perfil), SAO_PAULO).token();
        };
    }
}
