package com.zelo.ocorrencia.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.ocorrencia.application.Visoes.ComentarioVisao;
import com.zelo.ocorrencia.application.Visoes.ConteudoModeravel;
import com.zelo.ocorrencia.application.Visoes.TipoDeConteudo;
import com.zelo.ocorrencia.domain.Comentario;
import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.ConteudoRemovidoPelaModeracao;
import com.zelo.ocorrencia.infra.ComentarioRepository;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia;
import com.zelo.ocorrencia.infra.OcorrenciaRepository;
import com.zelo.usuario.application.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Comentários (RF12). Nunca anônimos: exibem o nome do autor. */
@Service
@RequiredArgsConstructor
public class ComentarioService {

    private final ComentarioRepository repository;
    private final OcorrenciaRepository ocorrencias;
    private final ConsultasDeOcorrencia consultas;
    private final UsuarioService usuarioService;
    private final ProvedorDeAtor provedorDeAtor;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<ComentarioVisao> listar(UUID ocorrenciaId, UUID usuarioId, Pageable pageable) {
        ocorrenciaVisivel(ocorrenciaId);
        Page<Comentario> pagina = repository.findByOcorrenciaIdAndRemovidoEmIsNullOrderByCriadoEmAsc(ocorrenciaId, pageable);
        Map<UUID, String> nomes = usuarioService.nomesPublicos(pagina.getContent().stream()
                .map(Comentario::getAutorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return pagina.map(c -> visao(c, nomes, usuarioId));
    }

    @Transactional
    public ComentarioVisao criar(UUID ocorrenciaId, UUID usuarioId, String texto) {
        provedorDeAtor.obterParticipante(usuarioId);
        Ocorrencia o = ocorrencias.findById(ocorrenciaId).orElseThrow(RegistroDeOcorrenciaService::naoEncontrada);
        o.exigirAceitaComentarios();
        Instant agora = clock.instant();
        Comentario comentario = repository.save(new Comentario(ocorrenciaId, usuarioId, texto, agora));
        consultas.registrarAtividade(ocorrenciaId, agora);
        return visao(comentario, usuarioService.nomesPublicos(java.util.Set.of(usuarioId)), usuarioId);
    }

    @Transactional
    public ComentarioVisao editar(UUID comentarioId, UUID usuarioId, String texto) {
        provedorDeAtor.obterParticipante(usuarioId);
        Comentario comentario = carregar(comentarioId);
        comentario.editar(usuarioId, texto, clock.instant());
        return visao(comentario, usuarioService.nomesPublicos(java.util.Set.of(usuarioId)), usuarioId);
    }

    @Transactional
    public void excluir(UUID comentarioId, UUID usuarioId) {
        provedorDeAtor.obter(usuarioId);
        carregar(comentarioId).excluirPeloAutor(usuarioId, clock.instant());
    }

    // ------------------------------------------------------------------ API para o módulo de moderação

    @Transactional(readOnly = true)
    public ConteudoModeravel conteudo(UUID comentarioId) {
        Comentario c = repository.findById(comentarioId).orElseThrow(ComentarioService::naoEncontrado);
        Ocorrencia o = ocorrencias.findById(c.getOcorrenciaId()).orElseThrow(RegistroDeOcorrenciaService::naoEncontrada);
        return new ConteudoModeravel(TipoDeConteudo.COMENTARIO, c.getId(), o.getId(), o.getCidadeId(),
                c.getAutorId(), c.getTexto(), c.estaRemovido(), o.getStatus());
    }

    @Transactional
    public void removerPelaModeracao(UUID comentarioId, Ator ator, String motivo) {
        Comentario c = carregar(comentarioId);
        Ocorrencia o = ocorrencias.findById(c.getOcorrenciaId()).orElseThrow(RegistroDeOcorrenciaService::naoEncontrada);
        if ((ator.perfil() != Perfil.MODERADOR && !ator.isAdmin()) || !ator.atuaNaCidade(o.getCidadeId())) {
            throw new AcessoNegadoException("FORA_DA_AREA_DE_ATUACAO", "Este comentário está fora da sua área de atuação.");
        }
        c.removerPelaModeracao(motivo, clock.instant());
        auditoria.registrar(ator, "COMENTARIO_REMOVIDO", "COMENTARIO", comentarioId, Map.of("motivo", motivo));
        eventos.publishEvent(new ConteudoRemovidoPelaModeracao(TipoDeConteudo.COMENTARIO.name(), comentarioId,
                o.getId(), c.getAutorId(), motivo));
    }

    private Comentario carregar(UUID id) {
        Comentario c = repository.findById(id).orElseThrow(ComentarioService::naoEncontrado);
        c.exigirNaoRemovido();
        return c;
    }

    private void ocorrenciaVisivel(UUID ocorrenciaId) {
        ocorrencias.findById(ocorrenciaId).orElseThrow(RegistroDeOcorrenciaService::naoEncontrada).exigirNaoRemovida();
    }

    private static ComentarioVisao visao(Comentario c, Map<UUID, String> nomes, UUID usuarioId) {
        String autor = c.getAutorId() == null ? MontadorDeVisoes.AUTOR_REMOVIDO
                : nomes.getOrDefault(c.getAutorId(), MontadorDeVisoes.AUTOR_REMOVIDO);
        boolean meu = usuarioId != null && usuarioId.equals(c.getAutorId());
        return new ComentarioVisao(c.getId(), c.getOcorrenciaId(), autor, meu, c.getTexto(), c.getCriadoEm(),
                c.getEditadoEm());
    }

    private static RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("COMENTARIO_NAO_ENCONTRADO", "Comentário não encontrado.");
    }
}
