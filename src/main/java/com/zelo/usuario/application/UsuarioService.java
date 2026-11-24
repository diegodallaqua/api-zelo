package com.zelo.usuario.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.NaoAutenticadoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.usuario.domain.EventosDeUsuario.UsuarioSuspenso;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** API pública do módulo de usuários para os demais módulos. */
@Service
@RequiredArgsConstructor
public class UsuarioService implements ProvedorDeAtor {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final UsuarioRepository repository;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    /** Dados do usuário para a moderação, que precisa identificar o autor para suspender. */
    public record DadosDoUsuario(UUID id, String nome, String email, Perfil perfil, boolean suspenso,
                                 Instant suspensoAte, boolean suspensaoPermanente, boolean excluido) {
    }

    /** Gestor e a cidade em que atua (a do seu endereço). */
    public record Gestor(UUID id, Integer cidadeId) {
    }

    @Override
    @Transactional(readOnly = true)
    public Ator obter(UUID usuarioId) {
        return paraAtor(usuarioAtivo(usuarioId));
    }

    @Override
    @Transactional(readOnly = true)
    public Ator obterParticipante(UUID usuarioId) {
        Usuario usuario = usuarioAtivo(usuarioId);
        Instant agora = clock.instant();
        if (usuario.estaSuspenso(agora)) {
            String ate = usuario.isSuspensaoPermanente()
                    ? "permanentemente"
                    : "até " + FORMATO_DATA.format(usuario.getSuspensoAte());
            throw new AcessoNegadoException("USUARIO_SUSPENSO",
                    "Sua conta está suspensa " + ate + ". Você pode navegar, mas não criar, curtir, comentar ou denunciar.");
        }
        return paraAtor(usuario);
    }

    /** Nomes públicos por id. Contas excluídas aparecem como "Usuário removido". */
    @Transactional(readOnly = true)
    public Map<UUID, String> nomesPublicos(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> nomes = new HashMap<>();
        repository.findAllById(ids).forEach(u -> nomes.put(u.getId(), u.getNome()));
        return nomes;
    }

    @Transactional(readOnly = true)
    public Map<UUID, DadosDoUsuario> dadosParaModeracao(Collection<UUID> ids) {
        Instant agora = clock.instant();
        Map<UUID, DadosDoUsuario> dados = new LinkedHashMap<>();
        repository.findAllById(ids).forEach(u -> dados.put(u.getId(), new DadosDoUsuario(u.getId(), u.getNome(),
                u.getEmail(), u.getPerfil(), u.estaSuspenso(agora), u.getSuspensoAte(), u.isSuspensaoPermanente(),
                u.estaExcluido())));
        return dados;
    }

    @Transactional(readOnly = true)
    public List<Gestor> listarGestoresAtivos() {
        Instant agora = clock.instant();
        return repository.findByPerfilAndExcluidoEmIsNull(Perfil.GESTOR).stream()
                .filter(u -> !u.estaSuspenso(agora) && u.cidadeId() != null)
                .map(u -> new Gestor(u.getId(), u.cidadeId()))
                .toList();
    }

    /**
     * Suspende um usuário (regra 3.5). Ninguém suspende administradores nem a si mesmo; moderadores
     * só suspendem cidadãos.
     */
    @Transactional
    public void suspender(UUID alvoId, Instant ate, boolean permanente, String motivo, Ator ator) {
        Usuario alvo = usuarioAtivoParaGestao(alvoId);
        exigirPodeModerarUsuario(alvo, ator);
        alvo.suspender(ate, permanente, motivo, clock.instant());
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("permanente", alvo.isSuspensaoPermanente());
        if (alvo.getSuspensoAte() != null) {
            detalhes.put("ate", alvo.getSuspensoAte());
        }
        detalhes.put("motivo", motivo);
        auditoria.registrar(ator, "USUARIO_SUSPENSO", "USUARIO", alvoId, detalhes);
        eventos.publishEvent(new UsuarioSuspenso(alvoId, alvo.getSuspensoAte(), permanente, motivo));
    }

    @Transactional
    public void reativar(UUID alvoId, Ator ator) {
        Usuario alvo = usuarioAtivoParaGestao(alvoId);
        exigirPodeModerarUsuario(alvo, ator);
        alvo.reativar(clock.instant());
        auditoria.registrar(ator, "USUARIO_REATIVADO", "USUARIO", alvoId, Map.of());
    }

    private void exigirPodeModerarUsuario(Usuario alvo, Ator ator) {
        if (alvo.getId().equals(ator.id())) {
            throw new RegraDeNegocioException("OPERACAO_SOBRE_SI_MESMO", "Você não pode suspender ou reativar a própria conta.");
        }
        if (alvo.getPerfil() == Perfil.ADMIN) {
            throw new AcessoNegadoException("ALVO_PROTEGIDO", "Administradores não podem ser suspensos.");
        }
        if (!ator.isAdmin() && alvo.getPerfil() != Perfil.CIDADAO) {
            throw new AcessoNegadoException("ALVO_PROTEGIDO", "Moderadores só podem suspender ou reativar cidadãos.");
        }
    }

    private Usuario usuarioAtivo(UUID usuarioId) {
        return repository.findById(usuarioId)
                .filter(u -> !u.estaExcluido())
                .orElseThrow(() -> new NaoAutenticadoException("CONTA_INDISPONIVEL",
                        "Sua conta não existe mais ou foi excluída. Faça login novamente."));
    }

    private Usuario usuarioAtivoParaGestao(UUID usuarioId) {
        return repository.findById(usuarioId)
                .filter(u -> !u.estaExcluido())
                .orElseThrow(() -> new RecursoNaoEncontradoException("USUARIO_NAO_ENCONTRADO",
                        "Usuário não encontrado: " + usuarioId + "."));
    }

    private static Ator paraAtor(Usuario u) {
        return new Ator(u.getId(), u.getPerfil(), u.cidadeId());
    }
}
