package com.zelo.moderacao.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.erro.RequisicaoInvalidaException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.moderacao.application.ModeracaoService.Decisao;
import com.zelo.moderacao.domain.Alvo;
import com.zelo.moderacao.domain.DecisaoModeracao;
import com.zelo.moderacao.domain.DecisaoModeracao.Acao;
import com.zelo.moderacao.domain.Denuncia;
import com.zelo.moderacao.domain.TipoConteudo;
import com.zelo.moderacao.infra.DecisaoModeracaoRepository;
import com.zelo.moderacao.infra.DenunciaRepository;
import com.zelo.moderacao.infra.FilaDeModeracao;
import com.zelo.ocorrencia.application.CicloDeVidaService;
import com.zelo.ocorrencia.application.ComentarioService;
import com.zelo.ocorrencia.application.Visoes.ConteudoModeravel;
import com.zelo.ocorrencia.application.Visoes.TipoDeConteudo;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import com.zelo.usuario.application.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModeracaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");
    private static final int SAO_PAULO = 3550308;
    private static final int RIO = 3304557;

    @Mock DenunciaRepository denuncias;
    @Mock DecisaoModeracaoRepository decisoes;
    @Mock FilaDeModeracao fila;
    @Mock DenunciaService denunciaService;
    @Mock CicloDeVidaService ocorrencias;
    @Mock ComentarioService comentarios;
    @Mock UsuarioService usuarios;
    @Mock ProvedorDeAtor provedorDeAtor;
    @Mock AuditoriaService auditoria;

    private final Ator moderador = new Ator(UUID.randomUUID(), Perfil.MODERADOR, SAO_PAULO);
    private final UUID autor = UUID.randomUUID();
    private final UUID ocorrenciaId = UUID.randomUUID();
    private final UUID comentarioId = UUID.randomUUID();
    private final Alvo alvoOcorrencia = Alvo.ocorrencia(ocorrenciaId);
    private final Alvo alvoComentario = Alvo.comentario(comentarioId);
    private ModeracaoService service;

    @BeforeEach
    void configurar() {
        service = new ModeracaoService(denuncias, decisoes, fila, denunciaService, ocorrencias, comentarios, usuarios,
                provedorDeAtor, auditoria, Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(provedorDeAtor.obterParticipante(moderador.id())).thenReturn(moderador);
        lenient().when(provedorDeAtor.obter(moderador.id())).thenReturn(moderador);
        lenient().when(decisoes.save(any())).thenAnswer(inv -> {
            DecisaoModeracao d = inv.getArgument(0);
            ReflectionTestUtils.setField(d, "id", UUID.randomUUID());
            return d;
        });
        lenient().when(denunciaService.conteudo(alvoOcorrencia)).thenReturn(conteudo(alvoOcorrencia, SAO_PAULO, autor, false));
        lenient().when(denunciaService.conteudo(alvoComentario)).thenReturn(conteudo(alvoComentario, SAO_PAULO, autor, false));
    }

    @Nested
    class DecisaoSobreDenuncias {

        @Test
        void manterResolveAsDenunciasComoImprocedentes() {
            List<Denuncia> pendentes = pendentes(alvoOcorrencia, 2);

            DecisaoModeracao d = service.decidir(moderador.id(), decisao(alvoOcorrencia, Acao.MANTER));

            assertThat(d.getAcao()).isEqualTo(Acao.MANTER);
            assertThat(d.getTipoConteudo()).isEqualTo(TipoConteudo.OCORRENCIA);
            assertThat(d.getOcorrenciaId()).isEqualTo(ocorrenciaId);
            assertThat(d.getComentarioId()).isNull();
            assertThat(d.getMotivo()).isEqualTo("Conteúdo legítimo");
            assertThat(pendentes).allSatisfy(den -> {
                assertThat(den.getStatus()).isEqualTo(Denuncia.Status.IMPROCEDENTE);
                assertThat(den.getDecisaoId()).isEqualTo(d.getId());
                assertThat(den.getResolvidaEm()).isEqualTo(AGORA);
            });
            verify(ocorrencias, never()).removerPelaModeracao(any(), any(), any());
            verify(auditoria).registrar(moderador, "DENUNCIAS_DECIDIDAS", "OCORRENCIA", ocorrenciaId,
                    Map.of("acao", "MANTER", "denuncias", 2, "resultado", "IMPROCEDENTE"));
        }

        @Test
        void removerComentarioUsaOModuloDeComentarios() {
            List<Denuncia> pendentes = pendentes(alvoComentario, 1);

            DecisaoModeracao d = service.decidir(moderador.id(), decisao(alvoComentario, Acao.REMOVER_CONTEUDO));

            verify(comentarios).removerPelaModeracao(comentarioId, moderador, "Conteúdo legítimo");
            verify(ocorrencias, never()).removerPelaModeracao(any(), any(), any());
            assertThat(d.getComentarioId()).isEqualTo(comentarioId);
            assertThat(d.getOcorrenciaId()).isNull();
            assertThat(pendentes.get(0).getStatus()).isEqualTo(Denuncia.Status.PROCEDENTE);
        }

        @Test
        void removerOcorrencia() {
            pendentes(alvoOcorrencia, 1);

            service.decidir(moderador.id(), decisao(alvoOcorrencia, Acao.REMOVER_CONTEUDO));

            verify(ocorrencias).removerPelaModeracao(ocorrenciaId, moderador, "Conteúdo legítimo");
        }

        @Test
        void conteudoJaRemovido() {
            when(denunciaService.conteudo(alvoOcorrencia)).thenReturn(conteudo(alvoOcorrencia, SAO_PAULO, autor, true));
            pendentes(alvoOcorrencia, 1);

            assertThatThrownBy(() -> service.decidir(moderador.id(), decisao(alvoOcorrencia, Acao.REMOVER_CONTEUDO)))
                    .isInstanceOf(ConflitoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "CONTEUDO_JA_REMOVIDO");
        }

        @Test
        void mesclarDuplicataGuardaAPrincipal() {
            pendentes(alvoOcorrencia, 1);
            UUID principal = UUID.randomUUID();

            DecisaoModeracao d = service.decidir(moderador.id(), new Decisao(alvoOcorrencia, Acao.MESCLAR_DUPLICATA,
                    "Mesmo buraco", principal, null, false, false));

            verify(ocorrencias).mesclar(ocorrenciaId, principal, moderador, "Mesmo buraco");
            assertThat(d.getOcorrenciaPrincipalId()).isEqualTo(principal);
        }

        @Test
        void soOcorrenciasSaoMescladas() {
            pendentes(alvoComentario, 1);

            assertThatThrownBy(() -> service.decidir(moderador.id(), new Decisao(alvoComentario, Acao.MESCLAR_DUPLICATA,
                    "x", UUID.randomUUID(), null, false, false)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasFieldOrPropertyWithValue("codigo", "MESCLAGEM_INVALIDA");
        }

        @Test
        void mesclagemExigeAPrincipal() {
            pendentes(alvoOcorrencia, 1);

            assertThatThrownBy(() -> service.decidir(moderador.id(), decisao(alvoOcorrencia, Acao.MESCLAR_DUPLICATA)))
                    .isInstanceOf(RequisicaoInvalidaException.class)
                    .hasFieldOrPropertyWithValue("codigo", "PRINCIPAL_OBRIGATORIA");
        }

        @Test
        void suspenderAutorERemoverOConteudo() {
            pendentes(alvoComentario, 1);
            Instant ate = AGORA.plusSeconds(86_400);

            DecisaoModeracao d = service.decidir(moderador.id(), new Decisao(alvoComentario, Acao.SUSPENDER_AUTOR,
                    "Ofensas repetidas", null, ate, false, true));

            verify(usuarios).suspender(autor, ate, false, "Ofensas repetidas", moderador);
            verify(comentarios).removerPelaModeracao(comentarioId, moderador, "Ofensas repetidas");
            assertThat(d.getUsuarioAfetadoId()).isEqualTo(autor);
            assertThat(d.getSuspensaoAte()).isEqualTo(ate);
        }

        @Test
        void suspensaoPermanenteNaoGuardaData() {
            pendentes(alvoOcorrencia, 1);

            DecisaoModeracao d = service.decidir(moderador.id(), new Decisao(alvoOcorrencia, Acao.SUSPENDER_AUTOR,
                    "Reincidência", null, AGORA.plusSeconds(60), true, false));

            assertThat(d.isSuspensaoPermanente()).isTrue();
            assertThat(d.getSuspensaoAte()).isNull();
            verify(ocorrencias, never()).removerPelaModeracao(any(), any(), any());
        }

        @Test
        void autorAnonimizadoNaoPodeSerSuspenso() {
            when(denunciaService.conteudo(alvoOcorrencia)).thenReturn(conteudo(alvoOcorrencia, SAO_PAULO, null, false));
            pendentes(alvoOcorrencia, 1);

            assertThatThrownBy(() -> service.decidir(moderador.id(), new Decisao(alvoOcorrencia, Acao.SUSPENDER_AUTOR,
                    "x", null, null, true, false)))
                    .hasFieldOrPropertyWithValue("codigo", "AUTOR_ANONIMIZADO");
            verifyNoInteractions(usuarios);
        }
    }

    @Nested
    class Validacoes {

        @Test
        void motivoEhObrigatorio() {
            assertThatThrownBy(() -> service.decidir(moderador.id(), new Decisao(alvoOcorrencia, Acao.MANTER, "  ",
                    null, null, false, false)))
                    .hasFieldOrPropertyWithValue("codigo", "MOTIVO_OBRIGATORIO");
            verify(decisoes, never()).save(any());
        }

        @Test
        void suspensaoDiretaNaoPassaPelasDecisoesDeConteudo() {
            assertThatThrownBy(() -> service.decidir(moderador.id(), decisao(alvoOcorrencia, Acao.SUSPENDER_USUARIO)))
                    .isInstanceOf(RequisicaoInvalidaException.class)
                    .hasFieldOrPropertyWithValue("codigo", "ACAO_INVALIDA");
        }

        @Test
        void conteudoForaDaCidadeDoModerador() {
            when(denunciaService.conteudo(alvoOcorrencia)).thenReturn(conteudo(alvoOcorrencia, RIO, autor, false));

            assertThatThrownBy(() -> service.decidir(moderador.id(), decisao(alvoOcorrencia, Acao.MANTER)))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "FORA_DA_AREA_DE_ATUACAO");
        }

        @Test
        void administradorDecideEmQualquerCidade() {
            Ator admin = new Ator(UUID.randomUUID(), Perfil.ADMIN, SAO_PAULO);
            when(provedorDeAtor.obterParticipante(admin.id())).thenReturn(admin);
            when(denunciaService.conteudo(alvoOcorrencia)).thenReturn(conteudo(alvoOcorrencia, RIO, autor, false));
            pendentes(alvoOcorrencia, 1);

            assertThat(service.decidir(admin.id(), decisao(alvoOcorrencia, Acao.MANTER)).getModeradorId())
                    .isEqualTo(admin.id());
        }

        @Test
        void semDenunciasPendentes() {
            when(denuncias.findByOcorrenciaIdAndStatus(ocorrenciaId, Denuncia.Status.PENDENTE)).thenReturn(List.of());

            assertThatThrownBy(() -> service.decidir(moderador.id(), decisao(alvoOcorrencia, Acao.MANTER)))
                    .isInstanceOf(ConflitoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "SEM_DENUNCIAS_PENDENTES");
        }
    }

    @Nested
    class AcoesDiretas {

        @Test
        void suspensaoDiretaNaoTemConteudoAlvo() {
            UUID alvo = UUID.randomUUID();

            DecisaoModeracao d = service.suspender(moderador.id(), alvo, null, true, " Spam em massa ");

            verify(usuarios).suspender(alvo, null, true, "Spam em massa", moderador);
            assertThat(d.getAcao()).isEqualTo(Acao.SUSPENDER_USUARIO);
            assertThat(d.getTipoConteudo()).isNull();
            assertThat(d.getOcorrenciaId()).isNull();
            assertThat(d.getComentarioId()).isNull();
            assertThat(d.getUsuarioAfetadoId()).isEqualTo(alvo);
        }

        @Test
        void reativacaoDireta() {
            UUID alvo = UUID.randomUUID();

            DecisaoModeracao d = service.reativar(moderador.id(), alvo, "Recurso aceito");

            verify(usuarios).reativar(alvo, moderador);
            assertThat(d.getAcao()).isEqualTo(Acao.REATIVAR_USUARIO);
            assertThat(d.getTipoConteudo()).isNull();
        }

        @Test
        void suspensaoDiretaExigeMotivo() {
            assertThatThrownBy(() -> service.suspender(moderador.id(), UUID.randomUUID(), null, true, null))
                    .hasFieldOrPropertyWithValue("codigo", "MOTIVO_OBRIGATORIO");
            verifyNoInteractions(usuarios);
        }

        @Test
        void mesclagemDiretaResolveAsDenunciasDaDuplicada() {
            List<Denuncia> pendentes = pendentes(alvoOcorrencia, 1);
            UUID principal = UUID.randomUUID();

            DecisaoModeracao d = service.mesclar(moderador.id(), ocorrenciaId, principal, "Mesmo local");

            verify(ocorrencias).mesclar(ocorrenciaId, principal, moderador, "Mesmo local");
            assertThat(d.getAcao()).isEqualTo(Acao.MESCLAR_DUPLICATA);
            assertThat(pendentes.get(0).getStatus()).isEqualTo(Denuncia.Status.PROCEDENTE);
        }
    }

    @Nested
    class Fila {

        @Test
        void moderadorNaoVeAFilaDeOutraCidade() {
            assertThatThrownBy(() -> service.fila(moderador.id(), RIO, PageRequest.of(0, 20)))
                    .hasFieldOrPropertyWithValue("codigo", "FORA_DA_AREA_DE_ATUACAO");
            verifyNoInteractions(fila);
        }

        @Test
        void moderadorVeASuaCidadeMesmoSemInformar() {
            when(fila.pendentes(SAO_PAULO, 0, 20)).thenReturn(List.of(new FilaDeModeracao.ItemDaFila(
                    TipoConteudo.OCORRENCIA, ocorrenciaId, null, SAO_PAULO, 3, List.of(Denuncia.Motivo.SPAM),
                    AGORA.minusSeconds(60), AGORA)));
            when(fila.contarConteudosPendentes(SAO_PAULO)).thenReturn(1L);
            when(usuarios.dadosParaModeracao(any())).thenReturn(Map.of());

            var pagina = service.fila(moderador.id(), null, PageRequest.of(0, 20));

            assertThat(pagina.getTotalElements()).isEqualTo(1);
            assertThat(pagina.getContent()).singleElement().satisfies(item -> {
                assertThat(item.ocorrenciaId()).isEqualTo(ocorrenciaId);
                assertThat(item.totalDenuncias()).isEqualTo(3);
                assertThat(item.texto()).isEqualTo("Texto do conteúdo");
            });
        }

        @Test
        void administradorVeTodasAsCidades() {
            Ator admin = new Ator(UUID.randomUUID(), Perfil.ADMIN, SAO_PAULO);
            when(provedorDeAtor.obter(admin.id())).thenReturn(admin);
            when(fila.pendentes(null, 0, 20)).thenReturn(List.of());

            service.fila(admin.id(), null, PageRequest.of(0, 20));

            verify(fila).contarConteudosPendentes(null);
        }
    }

    @Test
    void denunciasDoConteudoRespeitamAAreaDeAtuacao() {
        when(denunciaService.conteudo(alvoComentario)).thenReturn(conteudo(alvoComentario, RIO, autor, false));

        assertThatThrownBy(() -> service.denunciasDoConteudo(moderador.id(), alvoComentario))
                .hasFieldOrPropertyWithValue("codigo", "FORA_DA_AREA_DE_ATUACAO");
        verify(denuncias, never()).findByComentarioIdOrderByCriadaEmDesc(any());
        verify(auditoria, never()).registrar(any(), anyString(), anyString(), any(), anyMap());
    }

    private static Decisao decisao(Alvo alvo, Acao acao) {
        return new Decisao(alvo, acao, " Conteúdo legítimo ", null, null, false, false);
    }

    private List<Denuncia> pendentes(Alvo alvo, int quantidade) {
        List<Denuncia> lista = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            lista.add(new Denuncia(UUID.randomUUID(), alvo, SAO_PAULO, Denuncia.Motivo.SPAM, null, AGORA.minusSeconds(600)));
        }
        if (alvo.tipo() == TipoConteudo.OCORRENCIA) {
            lenient().when(denuncias.findByOcorrenciaIdAndStatus(alvo.ocorrenciaId(), Denuncia.Status.PENDENTE))
                    .thenReturn(lista);
        } else {
            lenient().when(denuncias.findByComentarioIdAndStatus(alvo.comentarioId(), Denuncia.Status.PENDENTE))
                    .thenReturn(lista);
        }
        return lista;
    }

    private static ConteudoModeravel conteudo(Alvo alvo, int cidade, UUID autorId, boolean removido) {
        TipoDeConteudo tipo = alvo.tipo() == TipoConteudo.OCORRENCIA ? TipoDeConteudo.OCORRENCIA : TipoDeConteudo.COMENTARIO;
        return new ConteudoModeravel(tipo, alvo.id(), UUID.randomUUID(), cidade, autorId, "Texto do conteúdo", removido,
                StatusOcorrencia.ABERTA);
    }
}
