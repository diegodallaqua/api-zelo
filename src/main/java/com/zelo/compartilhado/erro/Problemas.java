package com.zelo.compartilhado.erro;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.time.Instant;
import java.util.Map;

/**
 * Monta respostas de erro no formato RFC 9457 (Problem Details), sempre com {@code codigo},
 * {@code detail} em português e {@code timestamp}.
 */
public final class Problemas {

    private static final Map<Integer, String> TITULOS = Map.ofEntries(
            Map.entry(400, "Requisição inválida"),
            Map.entry(401, "Não autenticado"),
            Map.entry(403, "Acesso negado"),
            Map.entry(404, "Não encontrado"),
            Map.entry(405, "Método não permitido"),
            Map.entry(406, "Formato de resposta não aceito"),
            Map.entry(409, "Conflito"),
            Map.entry(413, "Conteúdo muito grande"),
            Map.entry(415, "Tipo de mídia não suportado"),
            Map.entry(422, "Regra de negócio violada"),
            Map.entry(429, "Muitas requisições"),
            Map.entry(500, "Erro interno"),
            Map.entry(503, "Serviço indisponível"));

    private Problemas() {
    }

    public static ProblemDetail criar(HttpStatusCode status, String codigo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo(status));
        problema.setProperty("codigo", codigo);
        problema.setProperty("timestamp", Instant.now());
        return problema;
    }

    public static String titulo(HttpStatusCode status) {
        String titulo = TITULOS.get(status.value());
        if (titulo != null) {
            return titulo;
        }
        HttpStatus conhecido = HttpStatus.resolve(status.value());
        return conhecido != null ? conhecido.getReasonPhrase() : "Erro";
    }
}
