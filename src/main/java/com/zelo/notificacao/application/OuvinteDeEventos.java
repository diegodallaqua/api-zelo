package com.zelo.notificacao.application;

import com.zelo.notificacao.domain.Notificacao.Tipo;
import com.zelo.ocorrencia.application.ConsultaDeOcorrenciaService;
import com.zelo.ocorrencia.domain.HistoricoStatus.TipoEvento;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.AvisoDeArquivamento;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.ConteudoRemovidoPelaModeracao;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.OcorrenciaMesclada;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.StatusAlterado;
import com.zelo.usuario.domain.EventosDeUsuario.ContaExcluida;
import com.zelo.usuario.domain.EventosDeUsuario.UsuarioSuspenso;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Transforma eventos de domínio em notificações (RF17). Roda após o commit, em transação própria:
 * uma falha ao notificar nunca desfaz a ação que gerou o evento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OuvinteDeEventos {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Sao_Paulo"));

    private final NotificacaoService notificacoes;
    private final ConsultaDeOcorrenciaService ocorrencias;

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAlterarStatus(StatusAlterado e) {
        executarComSeguranca(() -> {
            Set<UUID> destinatarios = new LinkedHashSet<>(ocorrencias.interessados(e.ocorrenciaId()));
            if (e.tipo() == TipoEvento.CONTESTACAO && e.autorOcorrenciaId() != null) {
                destinatarios.remove(e.autorOcorrenciaId());
            }
            String transicao = "de \"" + texto(e.anterior()) + "\" para \"" + texto(e.novo()) + "\"";
            String motivo = e.motivo() == null ? "" : " Motivo: " + e.motivo();
            notificacoes.notificar(destinatarios, Tipo.STATUS_ALTERADO, "Ocorrência atualizada",
                    usuario -> (usuario.equals(e.autorOcorrenciaId())
                            ? "Sua ocorrência mudou " : "Uma ocorrência que você apoia mudou ") + transicao + "." + motivo,
                    e.ocorrenciaId());
        });
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAvisarArquivamento(AvisoDeArquivamento e) {
        if (e.autorId() == null) {
            return;
        }
        executarComSeguranca(() -> notificacoes.notificar(Set.of(e.autorId()), Tipo.AVISO_ARQUIVAMENTO,
                "Ocorrência sem atualização",
                u -> "Sua ocorrência está há muito tempo sem atualização e será arquivada se nada mudar. "
                        + "Se o problema continua, comente ou atualize a ocorrência.",
                e.ocorrenciaId()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoMesclar(OcorrenciaMesclada e) {
        if (e.autorDuplicadaId() == null) {
            return;
        }
        executarComSeguranca(() -> notificacoes.notificar(Set.of(e.autorDuplicadaId()), Tipo.OCORRENCIA_MESCLADA,
                "Ocorrência unida a outra",
                u -> "Sua ocorrência relata o mesmo problema de outra já registrada e foi unida a ela. "
                        + "Você passa a apoiar a ocorrência principal.",
                e.principalId()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRemoverConteudo(ConteudoRemovidoPelaModeracao e) {
        if (e.autorId() == null) {
            return;
        }
        String conteudo = "OCORRENCIA".equals(e.tipoConteudo()) ? "Sua ocorrência" : "Seu comentário";
        executarComSeguranca(() -> notificacoes.notificar(Set.of(e.autorId()), Tipo.CONTEUDO_REMOVIDO,
                "Conteúdo removido pela moderação", u -> conteudo + " foi removido pela moderação. Motivo: " + e.motivo(),
                null));
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoSuspender(UsuarioSuspenso e) {
        String periodo = e.permanente() ? "permanentemente" : "até " + DATA.format(e.ate());
        executarComSeguranca(() -> notificacoes.notificar(Set.of(e.usuarioId()), Tipo.CONTA_SUSPENSA,
                "Conta suspensa", u -> "Sua conta foi suspensa " + periodo + ". Motivo: " + e.motivo(), null));
    }

    /** LGPD: apaga notificações, preferências e silenciamentos na mesma transação da exclusão da conta. */
    @EventListener
    public void aoExcluirConta(ContaExcluida e) {
        notificacoes.excluirDadosDoUsuario(e.usuarioId());
    }

    static String texto(StatusOcorrencia status) {
        return switch (status) {
            case EM_ANALISE -> "Em análise";
            case ABERTA -> "Aberta";
            case EM_ANDAMENTO -> "Em andamento";
            case RESOLVIDA -> "Resolvida";
            case REJEITADA -> "Rejeitada";
            case DUPLICADA -> "Duplicada";
            case ARQUIVADA -> "Arquivada";
        };
    }

    private static void executarComSeguranca(Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException ex) {
            log.error("Falha ao gerar notificação", ex);
        }
    }
}
