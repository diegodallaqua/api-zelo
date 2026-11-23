package com.zelo.ocorrencia.domain;

import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.ocorrencia.domain.HistoricoStatus.Papel;
import com.zelo.ocorrencia.domain.HistoricoStatus.TipoEvento;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.AvisoDeArquivamento;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.ConteudoRemovidoPelaModeracao;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.OcorrenciaCriada;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.OcorrenciaMesclada;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.StatusAlterado;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Agregado central. Todas as mudanças de status passam por métodos de domínio que validam a transição,
 * quem pode executá-la e gravam o histórico imutável; não há {@code setStatus}.
 * Os eventos de domínio ficam acumulados em {@link #extrairEventos()} e são publicados pelo serviço
 * (não via {@code save}: o merge de uma entidade gerenciada não funciona com filhos novos de id atribuído).
 * <p>
 * {@code totalCurtidas} e {@code prioridade} são atualizados só por SQL atômico (ver ConsultasDeOcorrencia);
 * com {@link DynamicUpdate} o UPDATE da entidade grava apenas as colunas alteradas e não sobrescreve esses contadores.
 */
@Entity
@DynamicUpdate
@Table(name = "ocorrencia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ocorrencia {

    public static final int DESCRICAO_MINIMO = 10;
    public static final int DESCRICAO_MAXIMO = 1000;

    @Id
    private UUID id;

    private UUID autorId;
    private UUID categoriaId;
    private Integer cidadeId;
    private String descricao;
    private double latitude;
    private double longitude;

    @Enumerated(EnumType.STRING)
    private StatusOcorrencia status;

    private boolean anonima;

    @Column(updatable = false)
    private int totalCurtidas;

    @Column(updatable = false)
    private int prioridade;
    private UUID ocorrenciaPrincipalId;
    private Instant resolvidaEm;
    private Instant resolucaoConfirmadaEm;
    private Instant ultimaAtividadeEm;
    private Instant avisoArquivamentoEm;
    private Instant removidaEm;
    private String motivoRemocao;
    private Instant criadaEm;
    private Instant atualizadaEm;

    @Version
    private Long versao;

    @Transient
    @Getter(AccessLevel.NONE)
    private final List<Object> eventos = new ArrayList<>();

    @OneToMany(mappedBy = "ocorrencia", cascade = CascadeType.PERSIST)
    @BatchSize(size = 50)
    @OrderBy("ocorridoEm ASC")
    private List<HistoricoStatus> historico = new ArrayList<>();

    @OneToMany(mappedBy = "ocorrencia", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("ordem ASC")
    private List<FotoOcorrencia> fotos = new ArrayList<>();

    // ------------------------------------------------------------------ criação

    public static Ocorrencia registrar(UUID autorId, UUID categoriaId, Integer cidadeId, String descricao,
                                       double latitude, double longitude, boolean anonima, Instant agora) {
        Ocorrencia o = new Ocorrencia();
        o.id = UUID.randomUUID();
        o.autorId = Objects.requireNonNull(autorId);
        o.categoriaId = Objects.requireNonNull(categoriaId);
        o.cidadeId = Objects.requireNonNull(cidadeId);
        o.descricao = descricao.trim();
        o.latitude = latitude;
        o.longitude = longitude;
        o.anonima = anonima;
        o.status = StatusOcorrencia.ABERTA;
        o.criadaEm = agora;
        o.atualizadaEm = agora;
        o.ultimaAtividadeEm = agora;
        o.historico.add(new HistoricoStatus(o, TipoEvento.CRIACAO, null, StatusOcorrencia.ABERTA, autorId,
                Papel.AUTOR, null, agora));
        o.registrarEvento(new OcorrenciaCriada(o.id, cidadeId, categoriaId));
        return o;
    }

    // ------------------------------------------------------------------ fotos

    public FotoOcorrencia adicionarFoto(UUID fotoId, String chave, String contentType, int tamanhoBytes, int largura,
                                        int altura, int maximo, Instant agora) {
        if (fotos.size() >= maximo) {
            throw new RegraDeNegocioException("LIMITE_DE_FOTOS",
                    "Uma ocorrência pode ter no máximo " + maximo + " foto(s).");
        }
        int ordem = fotos.stream().mapToInt(FotoOcorrencia::getOrdem).max().orElse(-1) + 1;
        FotoOcorrencia foto = new FotoOcorrencia(fotoId, this, chave, contentType, tamanhoBytes, largura, altura,
                ordem, agora);
        fotos.add(foto);
        atualizadaEm = agora;
        return foto;
    }

    /** Valida se o autor pode incluir mais uma foto, antes de processar e enviar o arquivo. */
    public void exigirPodeIncluirFoto(UUID usuarioId, int maximo) {
        exigirAutorPodeEditar(usuarioId);
        if (fotos.size() >= maximo) {
            throw new RegraDeNegocioException("LIMITE_DE_FOTOS",
                    "Uma ocorrência pode ter no máximo " + maximo + " foto(s).");
        }
    }

    public FotoOcorrencia removerFoto(UUID usuarioId, UUID fotoId, int minimo, Instant agora) {
        exigirAutorPodeEditar(usuarioId);
        FotoOcorrencia foto = fotos.stream().filter(f -> f.getId().equals(fotoId)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("FOTO_NAO_ENCONTRADA",
                        "Foto não encontrada nesta ocorrência."));
        if (fotos.size() <= minimo) {
            throw new RegraDeNegocioException("FOTO_OBRIGATORIA",
                    "A ocorrência precisa de ao menos " + minimo + " foto(s). Adicione outra antes de remover esta.");
        }
        fotos.remove(foto);
        registrarAtividade(agora);
        return foto;
    }

    public List<FotoOcorrencia> getFotos() {
        return Collections.unmodifiableList(fotos);
    }

    public List<HistoricoStatus> getHistorico() {
        return Collections.unmodifiableList(historico);
    }

    // ------------------------------------------------------------------ ações do autor

    public void editar(UUID usuarioId, String novaDescricao, UUID novaCategoriaId, Instant agora) {
        exigirAutorPodeEditar(usuarioId);
        if (novaDescricao != null) {
            this.descricao = novaDescricao.trim();
        }
        if (novaCategoriaId != null) {
            this.categoriaId = novaCategoriaId;
        }
        registrarAtividade(agora);
    }

    public void excluirPeloAutor(UUID usuarioId, Instant agora) {
        exigirAutorPodeEditar(usuarioId);
        this.removidaEm = agora;
        this.motivoRemocao = "Excluída pelo autor";
        this.atualizadaEm = agora;
    }

    /** RF16: o autor contesta a resolução dentro do prazo; a ocorrência volta a Aberta. */
    public void contestarResolucao(UUID usuarioId, String motivo, Duration prazo, Instant agora) {
        exigirResolucaoPendenteDoAutor(usuarioId, prazo, agora);
        exigirMotivo(motivo, "A contestação exige um motivo.");
        aplicar(StatusOcorrencia.ABERTA, TipoEvento.CONTESTACAO, Papel.AUTOR, usuarioId, motivo, agora);
    }

    /** RF16: a confirmação do autor encerra a ocorrência; não há mais contestação nem reabertura. */
    public void confirmarResolucao(UUID usuarioId, Duration prazo, Instant agora) {
        exigirResolucaoPendenteDoAutor(usuarioId, prazo, agora);
        this.resolucaoConfirmadaEm = agora;
        historico.add(new HistoricoStatus(this, TipoEvento.CONFIRMACAO, status, status, usuarioId, Papel.AUTOR,
                null, agora));
        registrarAtividade(agora);
    }

    /** Fim do prazo de contestação, ou vazio se a ocorrência não está aguardando confirmação. */
    public Optional<Instant> prazoDeContestacao(Duration prazo) {
        if (status != StatusOcorrencia.RESOLVIDA || resolucaoConfirmadaEm != null || resolvidaEm == null) {
            return Optional.empty();
        }
        return Optional.of(resolvidaEm.plus(prazo));
    }

    // ------------------------------------------------------------------ ações da equipe

    /** Moderador aprova ocorrência em análise. */
    public void aprovar(Ator ator, String observacao, Instant agora) {
        transicionar(StatusOcorrencia.ABERTA, ator, observacao, agora);
    }

    public void iniciarAtendimento(Ator ator, String observacao, Instant agora) {
        transicionar(StatusOcorrencia.EM_ANDAMENTO, ator, observacao, agora);
    }

    public void resolver(Ator ator, String observacao, Instant agora) {
        transicionar(StatusOcorrencia.RESOLVIDA, ator, observacao, agora);
    }

    public void rejeitar(Ator ator, String motivo, Instant agora) {
        transicionar(StatusOcorrencia.REJEITADA, ator, motivo, agora);
    }

    /**
     * Transição feita pela equipe, validada pela matriz de {@link RegrasDeTransicao} e pela área de
     * atuação do ator. Duplicada só por {@link #marcarComoDuplicada}.
     */
    public void transicionar(StatusOcorrencia novo, Ator ator, String motivo, Instant agora) {
        exigirNaoRemovida();
        if (novo == StatusOcorrencia.DUPLICADA) {
            throw new RegraDeNegocioException("USE_MESCLAGEM",
                    "Para marcar como duplicada, use a mesclagem de ocorrências da moderação.");
        }
        if (!RegrasDeTransicao.existe(status, novo)) {
            throw new TransicaoDeStatusInvalidaException(status, novo);
        }
        if (!RegrasDeTransicao.permitida(status, novo, ator.perfil())) {
            throw new AcessoNegadoException("TRANSICAO_NAO_PERMITIDA_AO_PERFIL",
                    "O perfil " + ator.perfil() + " não pode alterar uma ocorrência de " + status + " para " + novo + ".");
        }
        exigirAreaDeAtuacao(ator);
        if (novo == StatusOcorrencia.REJEITADA) {
            exigirMotivo(motivo, "A rejeição exige um motivo.");
        }
        if (status == StatusOcorrencia.EM_ANDAMENTO && novo == StatusOcorrencia.ABERTA) {
            exigirMotivo(motivo, "Informe o motivo da interrupção do atendimento.");
        }
        if (status == StatusOcorrencia.RESOLVIDA) {
            if (resolucaoConfirmadaEm != null) {
                throw new ConflitoException("OCORRENCIA_ENCERRADA",
                        "A resolução já foi confirmada pelo autor e a ocorrência está encerrada.");
            }
            exigirMotivo(motivo, "Informe o motivo da reabertura.");
        }
        aplicar(novo, TipoEvento.TRANSICAO, papelDe(ator), ator.id(), motivo, agora);
    }

    /** Mescla esta ocorrência (duplicada) na principal (regra 3.3). */
    public void marcarComoDuplicada(Ocorrencia principal, Ator ator, String motivo, Instant agora) {
        exigirNaoRemovida();
        principal.exigirNaoRemovida();
        if (principal.id.equals(id)) {
            throw new RegraDeNegocioException("MESCLAGEM_INVALIDA", "Uma ocorrência não pode ser mesclada nela mesma.");
        }
        if (!RegrasDeTransicao.existe(status, StatusOcorrencia.DUPLICADA)) {
            throw new TransicaoDeStatusInvalidaException(status, StatusOcorrencia.DUPLICADA);
        }
        if (!RegrasDeTransicao.permitida(status, StatusOcorrencia.DUPLICADA, ator.perfil())) {
            throw new AcessoNegadoException("TRANSICAO_NAO_PERMITIDA_AO_PERFIL",
                    "Apenas moderadores e administradores podem mesclar ocorrências.");
        }
        exigirAreaDeAtuacao(ator);
        if (!principal.status.ativo()) {
            throw new RegraDeNegocioException("PRINCIPAL_INATIVA",
                    "A ocorrência principal precisa estar em análise, aberta ou em andamento.");
        }
        if (!principal.cidadeId.equals(cidadeId)) {
            throw new RegraDeNegocioException("CIDADES_DIFERENTES",
                    "Só é possível mesclar ocorrências da mesma cidade.");
        }
        exigirMotivo(motivo, "A mesclagem exige um motivo.");
        this.ocorrenciaPrincipalId = principal.id;
        aplicar(StatusOcorrencia.DUPLICADA, TipoEvento.TRANSICAO, papelDe(ator), ator.id(), motivo, agora);
        registrarEvento(new OcorrenciaMesclada(id, principal.id, autorId));
    }

    public void removerPelaModeracao(Ator ator, String motivo, Instant agora) {
        exigirNaoRemovida();
        if (ator.perfil() != Perfil.MODERADOR && !ator.isAdmin()) {
            throw new AcessoNegadoException("ACESSO_NEGADO", "Apenas moderadores podem remover conteúdo.");
        }
        exigirAreaDeAtuacao(ator);
        exigirMotivo(motivo, "A remoção exige um motivo.");
        this.removidaEm = agora;
        this.motivoRemocao = motivo;
        this.atualizadaEm = agora;
        registrarEvento(new ConteudoRemovidoPelaModeracao("OCORRENCIA", id, id, autorId, motivo));
    }

    // ------------------------------------------------------------------ rotinas do sistema

    /** Regra 3.4: aviso antes do arquivamento por falta de atualização. */
    public void avisarArquivamento(Instant agora) {
        if (status != StatusOcorrencia.ABERTA || removidaEm != null || avisoArquivamentoEm != null) {
            return;
        }
        this.avisoArquivamentoEm = agora;
        registrarEvento(new AvisoDeArquivamento(id, autorId));
    }

    public void arquivarPorInatividade(Instant agora) {
        if (status != StatusOcorrencia.ABERTA || removidaEm != null) {
            return;
        }
        aplicar(StatusOcorrencia.ARQUIVADA, TipoEvento.TRANSICAO, Papel.SISTEMA, null,
                "Arquivada automaticamente por falta de atualização.", agora);
    }

    /** Prioridade inicial, antes de persistir. Depois, só por SQL (rotina diária e curtidas). */
    public void definirPrioridadeInicial(int prioridade) {
        this.prioridade = prioridade;
    }

    // ------------------------------------------------------------------ consultas

    public boolean estaRemovida() {
        return removidaEm != null;
    }

    public boolean ehAutor(UUID usuarioId) {
        return usuarioId != null && usuarioId.equals(autorId);
    }

    public void exigirNaoRemovida() {
        if (removidaEm != null) {
            throw new RecursoNaoEncontradoException("OCORRENCIA_NAO_ENCONTRADA", "Ocorrência não encontrada.");
        }
    }

    /** Regras para "Também vejo esse problema" (RF11). */
    public void exigirPodeReceberCurtidaDe(UUID usuarioId) {
        exigirNaoRemovida();
        if (!status.ativo()) {
            throw new RegraDeNegocioException("OCORRENCIA_NAO_ATIVA",
                    "Só é possível curtir ocorrências em análise, abertas ou em andamento.");
        }
        if (ehAutor(usuarioId)) {
            throw new RegraDeNegocioException("CURTIDA_PROPRIA", "Você não pode curtir a própria ocorrência.");
        }
    }

    public void exigirAceitaComentarios() {
        exigirNaoRemovida();
        if (!status.aceitaComentarios()) {
            throw new RegraDeNegocioException("COMENTARIOS_ENCERRADOS",
                    "Ocorrências duplicadas ou arquivadas não aceitam novos comentários.");
        }
    }

    /** Devolve e limpa os eventos de domínio acumulados desde a última extração. */
    public List<Object> extrairEventos() {
        List<Object> pendentes = List.copyOf(eventos);
        eventos.clear();
        return pendentes;
    }

    // ------------------------------------------------------------------ internos

    private void registrarEvento(Object evento) {
        eventos.add(evento);
    }

    private void aplicar(StatusOcorrencia novo, TipoEvento tipo, Papel papel, UUID quem, String motivo, Instant agora) {
        StatusOcorrencia anterior = status;
        status = novo;
        if (novo == StatusOcorrencia.RESOLVIDA) {
            resolvidaEm = agora;
            resolucaoConfirmadaEm = null;
        } else if (anterior == StatusOcorrencia.RESOLVIDA) {
            resolvidaEm = null;
        }
        String motivoLimpo = motivo == null || motivo.isBlank() ? null : motivo.trim();
        historico.add(new HistoricoStatus(this, tipo, anterior, novo, quem, papel, motivoLimpo, agora));
        registrarAtividade(agora);
        registrarEvento(new StatusAlterado(id, autorId, anterior, novo, tipo, motivoLimpo));
    }

    private void registrarAtividade(Instant agora) {
        this.ultimaAtividadeEm = agora;
        this.avisoArquivamentoEm = null;
        this.atualizadaEm = agora;
    }

    private void exigirAutorPodeEditar(UUID usuarioId) {
        exigirNaoRemovida();
        if (!ehAutor(usuarioId)) {
            throw new AcessoNegadoException("SOMENTE_AUTOR", "Apenas o autor pode alterar esta ocorrência.");
        }
        if (!status.editavelPeloAutor()) {
            throw new RegraDeNegocioException("OCORRENCIA_NAO_EDITAVEL",
                    "A ocorrência só pode ser alterada pelo autor enquanto estiver em análise ou aberta.");
        }
    }

    private void exigirResolucaoPendenteDoAutor(UUID usuarioId, Duration prazo, Instant agora) {
        exigirNaoRemovida();
        if (!ehAutor(usuarioId)) {
            throw new AcessoNegadoException("SOMENTE_AUTOR", "Apenas o autor pode confirmar ou contestar a resolução.");
        }
        if (status != StatusOcorrencia.RESOLVIDA) {
            throw new ConflitoException("OCORRENCIA_NAO_RESOLVIDA", "A ocorrência não está resolvida.");
        }
        if (resolucaoConfirmadaEm != null) {
            throw new ConflitoException("OCORRENCIA_ENCERRADA", "A resolução já foi confirmada e a ocorrência está encerrada.");
        }
        if (agora.isAfter(resolvidaEm.plus(prazo))) {
            throw new RegraDeNegocioException("PRAZO_DE_CONTESTACAO_ENCERRADO",
                    "O prazo de " + prazo.toDays() + " dias para confirmar ou contestar a resolução terminou.");
        }
    }

    private void exigirAreaDeAtuacao(Ator ator) {
        if (!ator.podeGerir(cidadeId)) {
            throw new AcessoNegadoException("FORA_DA_AREA_DE_ATUACAO", "Esta ocorrência está fora da sua área de atuação.");
        }
    }

    private static void exigirMotivo(String motivo, String mensagem) {
        if (motivo == null || motivo.isBlank()) {
            throw new RegraDeNegocioException("MOTIVO_OBRIGATORIO", mensagem);
        }
    }

    private static Papel papelDe(Ator ator) {
        return switch (ator.perfil()) {
            case ADMIN -> Papel.ADMIN;
            case MODERADOR -> Papel.MODERADOR;
            case GESTOR -> Papel.GESTOR;
            case CIDADAO -> throw new AcessoNegadoException("ACESSO_NEGADO", "Cidadãos não alteram o status de ocorrências.");
        };
    }
}
