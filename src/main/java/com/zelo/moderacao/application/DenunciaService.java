package com.zelo.moderacao.application;

import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.moderacao.domain.Alvo;
import com.zelo.moderacao.domain.Denuncia;
import com.zelo.moderacao.infra.DenunciaRepository;
import com.zelo.ocorrencia.application.CicloDeVidaService;
import com.zelo.ocorrencia.application.ComentarioService;
import com.zelo.ocorrencia.application.Visoes.ConteudoModeravel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Qualquer usuário autenticado denuncia uma ocorrência ou um comentário (regra 3.5 / RF18).
 * Cada denúncia aponta para exatamente um dos dois.
 */
@Service
@RequiredArgsConstructor
public class DenunciaService {

    private final DenunciaRepository repository;
    private final CicloDeVidaService ocorrencias;
    private final ComentarioService comentarios;
    private final ProvedorDeAtor provedorDeAtor;
    private final Clock clock;

    @Transactional
    public Denuncia denunciar(UUID usuarioId, Alvo alvo, Denuncia.Motivo motivo, String descricao) {
        provedorDeAtor.obterParticipante(usuarioId);
        ConteudoModeravel conteudo = validar(usuarioId, alvo, motivo, descricao, null);
        return repository.save(new Denuncia(usuarioId, alvo, conteudo.cidadeId(), motivo, limpar(descricao),
                clock.instant()));
    }

    /** O denunciante corrige a própria denúncia enquanto ela está pendente. */
    @Transactional
    public Denuncia alterar(UUID denunciaId, UUID usuarioId, Alvo alvo, Denuncia.Motivo motivo, String descricao) {
        provedorDeAtor.obterParticipante(usuarioId);
        Denuncia denuncia = repository.findById(denunciaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("DENUNCIA_NAO_ENCONTRADA", "Denúncia não encontrada."));
        denuncia.exigirPodeAlterar(usuarioId);
        ConteudoModeravel conteudo = validar(usuarioId, alvo, motivo, descricao, denunciaId);
        denuncia.alterar(usuarioId, alvo, conteudo.cidadeId(), motivo, limpar(descricao), clock.instant());
        return denuncia;
    }

    ConteudoModeravel conteudo(Alvo alvo) {
        return switch (alvo.tipo()) {
            case OCORRENCIA -> ocorrencias.conteudo(alvo.ocorrenciaId());
            case COMENTARIO -> comentarios.conteudo(alvo.comentarioId());
        };
    }

    private ConteudoModeravel validar(UUID usuarioId, Alvo alvo, Denuncia.Motivo motivo, String descricao,
                                      UUID ignorarId) {
        ConteudoModeravel conteudo = conteudo(alvo);
        if (conteudo.removido()) {
            throw new RecursoNaoEncontradoException("CONTEUDO_NAO_ENCONTRADO", "Conteúdo não encontrado.");
        }
        if (usuarioId.equals(conteudo.autorId())) {
            throw new RegraDeNegocioException("DENUNCIA_PROPRIA", "Você não pode denunciar o próprio conteúdo.");
        }
        if (motivo == Denuncia.Motivo.OUTRO && limpar(descricao) == null) {
            throw new RegraDeNegocioException("DESCRICAO_OBRIGATORIA", "Descreva o problema quando o motivo for OUTRO.");
        }
        if (repository.existeDoUsuario(usuarioId, alvo.ocorrenciaId(), alvo.comentarioId(), ignorarId)) {
            throw new ConflitoException("DENUNCIA_EXISTENTE", "Você já denunciou este conteúdo.");
        }
        return conteudo;
    }

    private static String limpar(String descricao) {
        return descricao == null || descricao.isBlank() ? null : descricao.trim();
    }
}
