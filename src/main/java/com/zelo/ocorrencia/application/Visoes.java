package com.zelo.ocorrencia.application;

import com.zelo.categoria.application.CategoriaService.CategoriaResumo;
import com.zelo.localidade.application.CidadeService.CidadeResumo;
import com.zelo.ocorrencia.domain.HistoricoStatus;
import com.zelo.ocorrencia.domain.HistoricoStatus.Papel;
import com.zelo.ocorrencia.domain.HistoricoStatus.TipoEvento;
import com.zelo.ocorrencia.domain.StatusOcorrencia;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Modelos de leitura devolvidos pela API do módulo de ocorrências. */
public final class Visoes {

    private Visoes() {
    }

    public enum TipoDeConteudo { OCORRENCIA, COMENTARIO }

    public record FotoVisao(UUID id, String url, int largura, int altura) {
    }

    public record HistoricoVisao(TipoEvento tipo, StatusOcorrencia statusAnterior, StatusOcorrencia statusNovo,
                                 Papel papel, String motivo, Instant ocorridoEm) {

        static HistoricoVisao de(HistoricoStatus h) {
            return new HistoricoVisao(h.getTipoEvento(), h.getStatusAnterior(), h.getStatusNovo(), h.getPapelAutor(),
                    h.getMotivo(), h.getOcorridoEm());
        }
    }

    /**
     * Detalhe público (RF13). {@code autor} é "Anônimo" para publicação anônima e "Usuário removido" após
     * anonimização; nunca expõe e-mail nem id do autor. {@code minha} indica se o usuário autenticado é o autor.
     */
    public record OcorrenciaDetalhe(UUID id, StatusOcorrencia status, CategoriaResumo categoria,
                                    CidadeResumo cidade, String descricao, double latitude, double longitude,
                                    boolean anonima, String autor, boolean minha, List<FotoVisao> fotos,
                                    int totalCurtidas, Boolean curtidaPorMim, long totalComentarios,
                                    List<HistoricoVisao> historico, UUID ocorrenciaPrincipalId, Instant criadaEm,
                                    Instant atualizadaEm, Instant resolvidaEm, Instant resolucaoConfirmadaEm,
                                    Instant prazoContestacaoAte) {
    }

    /** Item de listagem (minhas ocorrências, painel do gestor). Sem dados do autor. */
    public record OcorrenciaResumo(UUID id, StatusOcorrencia status, CategoriaResumo categoria, Integer cidadeId,
                                   String descricao, double latitude, double longitude, int totalCurtidas,
                                   int prioridade, String fotoCapaUrl, Instant criadaEm, Instant ultimaAtividadeEm) {
    }

    public record MarcadorVisao(UUID id, double latitude, double longitude, StatusOcorrencia status, UUID categoriaId,
                                int totalCurtidas) {
    }

    /** RNF02: resposta enxuta; {@code truncado} indica que há mais itens na área do que o limite. */
    public record MapaVisao(List<MarcadorVisao> itens, int limite, boolean truncado) {
    }

    public record CurtidaVisao(UUID ocorrenciaId, int totalCurtidas, boolean curtidaPorMim) {
    }

    public record ComentarioVisao(UUID id, UUID ocorrenciaId, String autor, boolean meu, String texto,
                                  Instant criadoEm, Instant editadoEm) {
    }

    public record EstatisticaVisao(CategoriaResumo categoria, long total, long abertas, long emAndamento,
                                   long resolvidas, long arquivadas, Double tempoMedioResolucaoHoras) {
    }

    public record EstatisticasVisao(CidadeResumo cidade, EstatisticaVisao geral,
                                    List<EstatisticaVisao> porCategoria) {
    }

    /** Conteúdo denunciado, como a moderação o enxerga. */
    public record ConteudoModeravel(TipoDeConteudo tipo, UUID conteudoId, UUID ocorrenciaId, Integer cidadeId,
                                    UUID autorId, String texto, boolean removido, StatusOcorrencia statusOcorrencia) {
    }
}
