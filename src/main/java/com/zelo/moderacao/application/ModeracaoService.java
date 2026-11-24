package com.zelo.moderacao.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.erro.RequisicaoInvalidaException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.moderacao.domain.Alvo;
import com.zelo.moderacao.domain.DecisaoModeracao;
import com.zelo.moderacao.domain.DecisaoModeracao.Acao;
import com.zelo.moderacao.domain.Denuncia;
import com.zelo.moderacao.domain.TipoConteudo;
import com.zelo.moderacao.infra.DecisaoModeracaoRepository;
import com.zelo.moderacao.infra.DenunciaRepository;
import com.zelo.moderacao.infra.FilaDeModeracao;
import com.zelo.moderacao.infra.FilaDeModeracao.ItemDaFila;
import com.zelo.ocorrencia.application.CicloDeVidaService;
import com.zelo.ocorrencia.application.ComentarioService;
import com.zelo.ocorrencia.application.Visoes.ConteudoModeravel;
import com.zelo.usuario.application.UsuarioService;
import com.zelo.usuario.application.UsuarioService.DadosDoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fluxo do moderador (2.3 / RF19): analisa denúncias e decide manter, remover, mesclar duplicata ou
 * suspender o autor, sempre registrando o motivo. Cada decisão resolve todas as denúncias pendentes do conteúdo.
 */
@Service
@RequiredArgsConstructor
public class ModeracaoService {

    private final DenunciaRepository denuncias;
    private final DecisaoModeracaoRepository decisoes;
    private final FilaDeModeracao fila;
    private final DenunciaService denunciaService;
    private final CicloDeVidaService ocorrencias;
    private final ComentarioService comentarios;
    private final UsuarioService usuarios;
    private final ProvedorDeAtor provedorDeAtor;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public record ItemDaFilaVisao(TipoConteudo tipoConteudo, UUID ocorrenciaId, UUID comentarioId, Integer cidadeId,
                                  long totalDenuncias, List<Denuncia.Motivo> motivos, Instant primeiraEm,
                                  Instant ultimaEm, String texto, boolean removido, DadosDoUsuario autor) {
    }

    /** @param suspensaoAte usado em SUSPENDER_AUTOR quando não for permanente */
    public record Decisao(Alvo alvo, Acao acao, String motivo, UUID ocorrenciaPrincipalId, Instant suspensaoAte,
                          boolean suspensaoPermanente, boolean removerConteudo) {
    }

    @Transactional(readOnly = true)
    public Page<ItemDaFilaVisao> fila(UUID usuarioId, Integer cidadeId, Pageable pagina) {
        Ator ator = provedorDeAtor.obter(usuarioId);
        Integer cidade = cidadeDaFila(ator, cidadeId);
        List<ItemDaFila> itens = fila.pendentes(cidade, pagina.getPageNumber(), pagina.getPageSize());
        Map<UUID, ConteudoModeravel> conteudos = itens.stream().collect(Collectors.toMap(
                i -> alvoDe(i).id(), i -> denunciaService.conteudo(alvoDe(i)), (a, b) -> a));
        Map<UUID, DadosDoUsuario> autores = usuarios.dadosParaModeracao(conteudos.values().stream()
                .map(ConteudoModeravel::autorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<ItemDaFilaVisao> visoes = itens.stream().map(i -> {
            ConteudoModeravel c = conteudos.get(alvoDe(i).id());
            return new ItemDaFilaVisao(i.tipoConteudo(), i.ocorrenciaId(), i.comentarioId(), i.cidadeId(),
                    i.totalDenuncias(), i.motivos(), i.primeiraEm(), i.ultimaEm(), c.texto(), c.removido(),
                    c.autorId() == null ? null : autores.get(c.autorId()));
        }).toList();
        return new PageImpl<>(visoes, pagina, fila.contarConteudosPendentes(cidade));
    }

    @Transactional(readOnly = true)
    public List<Denuncia> denunciasDoConteudo(UUID usuarioId, Alvo alvo) {
        Ator ator = provedorDeAtor.obter(usuarioId);
        exigirAreaDeAtuacao(ator, denunciaService.conteudo(alvo));
        return alvo.tipo() == TipoConteudo.OCORRENCIA
                ? denuncias.findByOcorrenciaIdOrderByCriadaEmDesc(alvo.ocorrenciaId())
                : denuncias.findByComentarioIdOrderByCriadaEmDesc(alvo.comentarioId());
    }

    @Transactional
    public DecisaoModeracao decidir(UUID usuarioId, Decisao d) {
        Ator ator = provedorDeAtor.obterParticipante(usuarioId);
        exigirMotivo(d.motivo());
        if (!d.acao().sobreConteudo()) {
            throw new RequisicaoInvalidaException("ACAO_INVALIDA",
                    "Use as rotas de suspensão e reativação de usuário para esta ação.");
        }
        Alvo alvo = d.alvo();
        ConteudoModeravel conteudo = denunciaService.conteudo(alvo);
        exigirAreaDeAtuacao(ator, conteudo);
        List<Denuncia> pendentes = pendentes(alvo);
        if (pendentes.isEmpty()) {
            throw new ConflitoException("SEM_DENUNCIAS_PENDENTES", "Não há denúncias pendentes para este conteúdo.");
        }
        DecisaoModeracao.DecisaoModeracaoBuilder decisao = DecisaoModeracao.builder()
                .moderadorId(ator.id()).acao(d.acao()).alvo(alvo).motivo(d.motivo().trim()).decididaEm(clock.instant());
        Denuncia.Status resultado = Denuncia.Status.PROCEDENTE;
        switch (d.acao()) {
            case MANTER -> resultado = Denuncia.Status.IMPROCEDENTE;
            case REMOVER_CONTEUDO -> removerConteudo(alvo, ator, d.motivo(), conteudo);
            case MESCLAR_DUPLICATA -> {
                if (alvo.tipo() != TipoConteudo.OCORRENCIA) {
                    throw new RegraDeNegocioException("MESCLAGEM_INVALIDA", "Apenas ocorrências podem ser mescladas.");
                }
                if (d.ocorrenciaPrincipalId() == null) {
                    throw new RequisicaoInvalidaException("PRINCIPAL_OBRIGATORIA", "Informe 'ocorrenciaPrincipalId' para mesclar.");
                }
                ocorrencias.mesclar(alvo.ocorrenciaId(), d.ocorrenciaPrincipalId(), ator, d.motivo());
                decisao.ocorrenciaPrincipalId(d.ocorrenciaPrincipalId());
            }
            case SUSPENDER_AUTOR -> {
                if (conteudo.autorId() == null) {
                    throw new RegraDeNegocioException("AUTOR_ANONIMIZADO", "O autor deste conteúdo não pode mais ser identificado.");
                }
                usuarios.suspender(conteudo.autorId(), d.suspensaoAte(), d.suspensaoPermanente(), d.motivo(), ator);
                decisao.usuarioAfetadoId(conteudo.autorId()).suspensaoAte(d.suspensaoPermanente() ? null : d.suspensaoAte())
                        .suspensaoPermanente(d.suspensaoPermanente());
                if (d.removerConteudo() && !conteudo.removido()) {
                    removerConteudo(alvo, ator, d.motivo(), conteudo);
                }
            }
            default -> throw new IllegalStateException("Ação sem conteúdo: " + d.acao());
        }
        DecisaoModeracao salva = decisoes.save(decisao.build());
        Instant agora = clock.instant();
        Denuncia.Status status = resultado;
        pendentes.forEach(den -> den.resolver(status, salva.getId(), agora));
        auditoria.registrar(ator, "DENUNCIAS_DECIDIDAS", alvo.tipo().name(), alvo.id(),
                Map.of("acao", d.acao().name(), "denuncias", pendentes.size(), "resultado", status.name()));
        return salva;
    }

    /** Mesclagem direta pelo moderador, sem denúncia prévia (RF19). */
    @Transactional
    public DecisaoModeracao mesclar(UUID usuarioId, UUID duplicadaId, UUID principalId, String motivo) {
        Ator ator = provedorDeAtor.obterParticipante(usuarioId);
        exigirMotivo(motivo);
        ocorrencias.mesclar(duplicadaId, principalId, ator, motivo);
        Instant agora = clock.instant();
        Alvo alvo = Alvo.ocorrencia(duplicadaId);
        DecisaoModeracao decisao = decisoes.save(DecisaoModeracao.builder().moderadorId(ator.id())
                .acao(Acao.MESCLAR_DUPLICATA).alvo(alvo).ocorrenciaPrincipalId(principalId).motivo(motivo.trim())
                .decididaEm(agora).build());
        pendentes(alvo).forEach(den -> den.resolver(Denuncia.Status.PROCEDENTE, decisao.getId(), agora));
        return decisao;
    }

    @Transactional
    public DecisaoModeracao suspender(UUID usuarioId, UUID alvoId, Instant ate, boolean permanente, String motivo) {
        Ator ator = provedorDeAtor.obterParticipante(usuarioId);
        exigirMotivo(motivo);
        usuarios.suspender(alvoId, ate, permanente, motivo.trim(), ator);
        return decisoes.save(DecisaoModeracao.builder().moderadorId(ator.id()).acao(Acao.SUSPENDER_USUARIO)
                .usuarioAfetadoId(alvoId).suspensaoAte(permanente ? null : ate).suspensaoPermanente(permanente)
                .motivo(motivo.trim()).decididaEm(clock.instant()).build());
    }

    @Transactional
    public DecisaoModeracao reativar(UUID usuarioId, UUID alvoId, String motivo) {
        Ator ator = provedorDeAtor.obterParticipante(usuarioId);
        exigirMotivo(motivo);
        usuarios.reativar(alvoId, ator);
        return decisoes.save(DecisaoModeracao.builder().moderadorId(ator.id()).acao(Acao.REATIVAR_USUARIO)
                .usuarioAfetadoId(alvoId).motivo(motivo.trim()).decididaEm(clock.instant()).build());
    }

    /** Histórico de decisões sobre o usuário, para avaliar reincidência (regra 3.5). */
    @Transactional(readOnly = true)
    public List<DecisaoModeracao> historicoDoUsuario(UUID alvoId) {
        return decisoes.findByUsuarioAfetadoIdOrderByDecididaEmDesc(alvoId);
    }

    private List<Denuncia> pendentes(Alvo alvo) {
        return alvo.tipo() == TipoConteudo.OCORRENCIA
                ? denuncias.findByOcorrenciaIdAndStatus(alvo.ocorrenciaId(), Denuncia.Status.PENDENTE)
                : denuncias.findByComentarioIdAndStatus(alvo.comentarioId(), Denuncia.Status.PENDENTE);
    }

    private void removerConteudo(Alvo alvo, Ator ator, String motivo, ConteudoModeravel conteudo) {
        if (conteudo.removido()) {
            throw new ConflitoException("CONTEUDO_JA_REMOVIDO", "Este conteúdo já foi removido.");
        }
        switch (alvo.tipo()) {
            case OCORRENCIA -> ocorrencias.removerPelaModeracao(alvo.ocorrenciaId(), ator, motivo.trim());
            case COMENTARIO -> comentarios.removerPelaModeracao(alvo.comentarioId(), ator, motivo.trim());
        }
    }

    private static Alvo alvoDe(ItemDaFila i) {
        return new Alvo(i.tipoConteudo(), i.ocorrenciaId(), i.comentarioId());
    }

    private static Integer cidadeDaFila(Ator ator, Integer solicitada) {
        if (ator.isAdmin()) {
            return solicitada;
        }
        if (solicitada != null && !solicitada.equals(ator.cidadeId())) {
            throw new AcessoNegadoException("FORA_DA_AREA_DE_ATUACAO", "Você só pode moderar a sua cidade.");
        }
        return ator.cidadeId();
    }

    private static void exigirAreaDeAtuacao(Ator ator, ConteudoModeravel conteudo) {
        if (!ator.atuaNaCidade(conteudo.cidadeId())) {
            throw new AcessoNegadoException("FORA_DA_AREA_DE_ATUACAO", "Este conteúdo está fora da sua área de atuação.");
        }
    }

    private static void exigirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new RegraDeNegocioException("MOTIVO_OBRIGATORIO", "Registre o motivo da decisão.");
        }
    }
}
