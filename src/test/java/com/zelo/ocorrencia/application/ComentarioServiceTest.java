package com.zelo.ocorrencia.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.ocorrencia.application.Visoes.ComentarioVisao;
import com.zelo.ocorrencia.application.Visoes.ConteudoModeravel;
import com.zelo.ocorrencia.domain.Comentario;
import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.ConteudoRemovidoPelaModeracao;
import com.zelo.ocorrencia.infra.ComentarioRepository;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia;
import com.zelo.ocorrencia.infra.OcorrenciaRepository;
import com.zelo.usuario.application.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComentarioServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");
    private static final int SAO_PAULO = 3550308;
    private static final int RIO = 3304557;

    @Mock ComentarioRepository repository;
    @Mock OcorrenciaRepository ocorrencias;
    @Mock ConsultasDeOcorrencia consultas;
    @Mock UsuarioService usuarioService;
    @Mock ProvedorDeAtor provedorDeAtor;
    @Mock AuditoriaService auditoria;
    @Mock ApplicationEventPublisher eventos;

    private final UUID autorDaOcorrencia = UUID.randomUUID();
    private final UUID ana = UUID.randomUUID();
    private final Ator moderador = new Ator(UUID.randomUUID(), Perfil.MODERADOR, SAO_PAULO);
    private ComentarioService service;
    private Ocorrencia ocorrencia;

    @BeforeEach
    void configurar() {
        service = new ComentarioService(repository, ocorrencias, consultas, usuarioService, provedorDeAtor, auditoria,
                eventos, Clock.fixed(AGORA, ZoneOffset.UTC));
        ocorrencia = Ocorrencia.registrar(autorDaOcorrencia, UUID.randomUUID(), SAO_PAULO, "Buraco", -23.55, -46.63,
                false, AGORA.minusSeconds(3600));
        lenient().when(ocorrencias.findById(ocorrencia.getId())).thenReturn(Optional.of(ocorrencia));
        lenient().when(repository.save(any())).thenAnswer(inv -> comId(inv.getArgument(0)));
        lenient().when(usuarioService.nomesPublicos(any())).thenReturn(Map.of(ana, "Ana"));
    }

    @Nested
    class Criacao {

        @Test
        void comentaComONomeDoAutorERegistraAtividade() {
            ComentarioVisao visao = service.criar(ocorrencia.getId(), ana, "  Continua igual  ");

            assertThat(visao.autor()).isEqualTo("Ana");
            assertThat(visao.meu()).isTrue();
            assertThat(visao.texto()).isEqualTo("Continua igual");
            verify(provedorDeAtor).obterParticipante(ana);
            verify(consultas).registrarAtividade(ocorrencia.getId(), AGORA);
        }

        @Test
        void suspensoNaoComenta() {
            when(provedorDeAtor.obterParticipante(ana)).thenThrow(new AcessoNegadoException("USUARIO_SUSPENSO", "x"));

            assertThatThrownBy(() -> service.criar(ocorrencia.getId(), ana, "Oi"))
                    .hasFieldOrPropertyWithValue("codigo", "USUARIO_SUSPENSO");
            verify(repository, never()).save(any());
        }

        @Test
        void ocorrenciaDuplicadaNaoAceitaComentarios() {
            Ocorrencia principal = Ocorrencia.registrar(UUID.randomUUID(), UUID.randomUUID(), SAO_PAULO, "Buraco",
                    -23.55, -46.63, false, AGORA.minusSeconds(7200));
            ocorrencia.marcarComoDuplicada(principal, moderador, "Mesmo buraco", AGORA);

            assertThatThrownBy(() -> service.criar(ocorrencia.getId(), ana, "Oi"))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasFieldOrPropertyWithValue("codigo", "COMENTARIOS_ENCERRADOS");
        }

        @Test
        void ocorrenciaRemovidaPelaModeracaoSome() {
            ocorrencia.removerPelaModeracao(moderador, "Spam", AGORA);

            assertThatThrownBy(() -> service.criar(ocorrencia.getId(), ana, "Oi"))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "OCORRENCIA_NAO_ENCONTRADA");
        }

        @Test
        void ocorrenciaInexistente() {
            UUID id = UUID.randomUUID();
            when(ocorrencias.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.criar(id, ana, "Oi"))
                    .hasFieldOrPropertyWithValue("codigo", "OCORRENCIA_NAO_ENCONTRADA");
        }
    }

    @Nested
    class EdicaoEExclusao {

        private Comentario comentario;

        @BeforeEach
        void comentar() {
            comentario = comId(new Comentario(ocorrencia.getId(), ana, "Texto", AGORA.minusSeconds(60)));
            lenient().when(repository.findById(comentario.getId())).thenReturn(Optional.of(comentario));
        }

        @Test
        void autorEdita() {
            ComentarioVisao visao = service.editar(comentario.getId(), ana, " Texto corrigido ");

            assertThat(visao.texto()).isEqualTo("Texto corrigido");
            assertThat(comentario.getEditadoEm()).isEqualTo(AGORA);
        }

        @Test
        void outroUsuarioNaoEdita() {
            assertThatThrownBy(() -> service.editar(comentario.getId(), UUID.randomUUID(), "Hackeado"))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "SOMENTE_AUTOR");
            assertThat(comentario.getTexto()).isEqualTo("Texto");
        }

        @Test
        void comentarioExcluidoNaoEhEditado() {
            comentario.excluirPeloAutor(ana, AGORA.minusSeconds(1));

            assertThatThrownBy(() -> service.editar(comentario.getId(), ana, "Volta"))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "COMENTARIO_NAO_ENCONTRADO");
        }

        @Test
        void suspensoAindaExcluiOProprioComentario() {
            service.excluir(comentario.getId(), ana);

            assertThat(comentario.estaRemovido()).isTrue();
            assertThat(comentario.getMotivoRemocao()).isEqualTo("Excluído pelo autor");
            verify(provedorDeAtor).obter(ana);
            verify(provedorDeAtor, never()).obterParticipante(any());
        }
    }

    @Nested
    class RemocaoPelaModeracao {

        private Comentario comentario;

        @BeforeEach
        void comentar() {
            comentario = comId(new Comentario(ocorrencia.getId(), ana, "Ofensa", AGORA.minusSeconds(60)));
            lenient().when(repository.findById(comentario.getId())).thenReturn(Optional.of(comentario));
        }

        @Test
        void moderadorDaCidadeRemoveAvisaEAudita() {
            service.removerPelaModeracao(comentario.getId(), moderador, "Linguagem ofensiva");

            assertThat(comentario.estaRemovido()).isTrue();
            verify(eventos).publishEvent(new ConteudoRemovidoPelaModeracao("COMENTARIO", comentario.getId(),
                    ocorrencia.getId(), ana, "Linguagem ofensiva"));
            verify(auditoria).registrar(moderador, "COMENTARIO_REMOVIDO", "COMENTARIO", comentario.getId(),
                    Map.of("motivo", "Linguagem ofensiva"));
        }

        @Test
        void gestorNaoRemoveComentarios() {
            Ator gestor = new Ator(UUID.randomUUID(), Perfil.GESTOR, SAO_PAULO);

            assertThatThrownBy(() -> service.removerPelaModeracao(comentario.getId(), gestor, "x"))
                    .hasFieldOrPropertyWithValue("codigo", "FORA_DA_AREA_DE_ATUACAO");
            assertThat(comentario.estaRemovido()).isFalse();
        }

        @Test
        void moderadorDeOutraCidadeNaoRemove() {
            Ator doRio = new Ator(UUID.randomUUID(), Perfil.MODERADOR, RIO);

            assertThatThrownBy(() -> service.removerPelaModeracao(comentario.getId(), doRio, "x"))
                    .hasFieldOrPropertyWithValue("codigo", "FORA_DA_AREA_DE_ATUACAO");
            verify(eventos, never()).publishEvent(any(Object.class));
        }

        @Test
        void remocaoExigeMotivo() {
            assertThatThrownBy(() -> service.removerPelaModeracao(comentario.getId(), moderador, " "))
                    .hasFieldOrPropertyWithValue("codigo", "MOTIVO_OBRIGATORIO");
            verify(auditoria, never()).registrar(any(), anyString(), anyString(), any(), anyMap());
        }

        @Test
        void conteudoParaModeracaoTrazACidadeDaOcorrencia() {
            ConteudoModeravel c = service.conteudo(comentario.getId());

            assertThat(c.cidadeId()).isEqualTo(SAO_PAULO);
            assertThat(c.ocorrenciaId()).isEqualTo(ocorrencia.getId());
            assertThat(c.autorId()).isEqualTo(ana);
            assertThat(c.texto()).isEqualTo("Ofensa");
            assertThat(c.removido()).isFalse();
        }
    }

    @Test
    void listagemMostraUsuarioRemovidoParaContasExcluidas() {
        UUID excluido = UUID.randomUUID();
        Comentario daAna = comId(new Comentario(ocorrencia.getId(), ana, "Primeiro", AGORA.minusSeconds(120)));
        Comentario deExcluido = comId(new Comentario(ocorrencia.getId(), excluido, "Segundo", AGORA.minusSeconds(60)));
        PageRequest pagina = PageRequest.of(0, 20);
        when(repository.findByOcorrenciaIdAndRemovidoEmIsNullOrderByCriadoEmAsc(ocorrencia.getId(), pagina))
                .thenReturn(new PageImpl<>(List.of(daAna, deExcluido), pagina, 2));

        List<ComentarioVisao> visoes = service.listar(ocorrencia.getId(), ana, pagina).getContent();

        assertThat(visoes).extracting(ComentarioVisao::autor).containsExactly("Ana", MontadorDeVisoes.AUTOR_REMOVIDO);
        assertThat(visoes).extracting(ComentarioVisao::meu).containsExactly(true, false);
        verify(usuarioService).nomesPublicos(eq(java.util.Set.of(ana, excluido)));
    }

    private static Comentario comId(Comentario c) {
        if (c.getId() == null) {
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
        }
        return c;
    }
}
