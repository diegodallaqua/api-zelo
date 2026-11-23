package com.zelo.notificacao.application;

import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.notificacao.domain.Notificacao;
import com.zelo.notificacao.domain.Notificador;
import com.zelo.notificacao.domain.PreferenciaNotificacao;
import com.zelo.notificacao.infra.NotificacaoRepository;
import com.zelo.notificacao.infra.PreferenciaNotificacaoRepository;
import com.zelo.notificacao.infra.SilenciamentoRepository;
import com.zelo.ocorrencia.application.CicloDeVidaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Notificações dentro do app, preferências de canal e silenciamento de ocorrências (regra 3.6). */
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository notificacoes;
    private final PreferenciaNotificacaoRepository preferencias;
    private final SilenciamentoRepository silenciamentos;
    private final CicloDeVidaService ocorrencias;
    private final Notificador notificador;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<Notificacao> listar(UUID usuarioId, boolean somenteNaoLidas, Pageable pageable) {
        return somenteNaoLidas
                ? notificacoes.findByUsuarioIdAndLidaEmIsNullOrderByCriadaEmDesc(usuarioId, pageable)
                : notificacoes.findByUsuarioIdOrderByCriadaEmDesc(usuarioId, pageable);
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(UUID usuarioId) {
        return notificacoes.countByUsuarioIdAndLidaEmIsNull(usuarioId);
    }

    @Transactional
    public void marcarComoLida(UUID notificacaoId, UUID usuarioId) {
        notificacoes.findById(notificacaoId)
                .filter(n -> n.getUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("NOTIFICACAO_NAO_ENCONTRADA",
                        "Notificação não encontrada."))
                .marcarComoLida(clock.instant());
    }

    @Transactional
    public void marcarTodasComoLidas(UUID usuarioId) {
        notificacoes.marcarTodasComoLidas(usuarioId, clock.instant());
    }

    @Transactional(readOnly = true)
    public PreferenciaNotificacao preferencias(UUID usuarioId) {
        return preferencias.findById(usuarioId).orElseGet(() -> PreferenciaNotificacao.padrao(usuarioId, clock.instant()));
    }

    @Transactional
    public PreferenciaNotificacao alterarPreferencias(UUID usuarioId, boolean push, boolean email) {
        Instant agora = clock.instant();
        PreferenciaNotificacao preferencia = preferencias.findById(usuarioId)
                .orElseGet(() -> PreferenciaNotificacao.padrao(usuarioId, agora));
        preferencia.alterar(push, email, agora);
        return preferencias.save(preferencia);
    }

    @Transactional
    public void silenciar(UUID usuarioId, UUID ocorrenciaId) {
        if (ocorrencias.conteudo(ocorrenciaId).removido()) {
            throw new RecursoNaoEncontradoException("OCORRENCIA_NAO_ENCONTRADA", "Ocorrência não encontrada.");
        }
        silenciamentos.silenciar(usuarioId, ocorrenciaId, clock.instant());
    }

    @Transactional
    public void reativar(UUID usuarioId, UUID ocorrenciaId) {
        silenciamentos.reativar(usuarioId, ocorrenciaId);
    }

    @Transactional(readOnly = true)
    public List<UUID> silenciadas(UUID usuarioId) {
        return silenciamentos.silenciadasPor(usuarioId);
    }

    /**
     * Grava a notificação para cada destinatário e despacha pelos canais escolhidos. Em notificações de
     * ocorrência, quem silenciou a ocorrência não recebe nada.
     */
    @Transactional
    public void notificar(Collection<UUID> destinatarios, Notificacao.Tipo tipo, String titulo,
                          Function<UUID, String> mensagemPara, UUID ocorrenciaId) {
        if (destinatarios.isEmpty()) {
            return;
        }
        var silenciaram = ocorrenciaId == null ? java.util.Set.<UUID>of() : silenciamentos.quemSilenciou(ocorrenciaId);
        Instant agora = clock.instant();
        Map<UUID, PreferenciaNotificacao> prefs = preferencias.findAllById(destinatarios).stream()
                .collect(Collectors.toMap(PreferenciaNotificacao::getUsuarioId, Function.identity()));
        for (UUID usuarioId : destinatarios) {
            if (silenciaram.contains(usuarioId)) {
                continue;
            }
            String mensagem = mensagemPara.apply(usuarioId);
            notificacoes.save(new Notificacao(usuarioId, tipo, titulo, mensagem, ocorrenciaId, agora));
            PreferenciaNotificacao preferencia = prefs.getOrDefault(usuarioId, PreferenciaNotificacao.padrao(usuarioId, agora));
            notificador.enviar(new Notificador.Envio(usuarioId, preferencia.canais(), titulo, mensagem, ocorrenciaId));
        }
    }

    @Transactional
    public void excluirDadosDoUsuario(UUID usuarioId) {
        notificacoes.excluirDoUsuario(usuarioId);
        silenciamentos.excluirDoUsuario(usuarioId);
        preferencias.deleteById(usuarioId);
    }
}
