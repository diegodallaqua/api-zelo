package com.zelo.moderacao.application;

import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.moderacao.domain.Alvo;
import com.zelo.moderacao.domain.Denuncia;
import com.zelo.moderacao.domain.Denuncia.Motivo;
import com.zelo.moderacao.domain.TipoConteudo;
import com.zelo.moderacao.infra.DenunciaRepository;
import com.zelo.ocorrencia.application.CicloDeVidaService;
import com.zelo.ocorrencia.application.ComentarioService;
import com.zelo.ocorrencia.application.Visoes.ConteudoModeravel;
import com.zelo.ocorrencia.application.Visoes.TipoDeConteudo;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DenunciaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");
    private static final int SAO_PAULO = 3550308;

    @Mock DenunciaRepository repository;
    @Mock CicloDeVidaService ocorrencias;
    @Mock ComentarioService comentarios;
    @Mock ProvedorDeAtor provedorDeAtor;

    private final UUID denunciante = UUID.randomUUID();
    private final UUID autor = UUID.randomUUID();
    private final UUID ocorrenciaId = UUID.randomUUID();
    private final UUID comentarioId = UUID.randomUUID();
    private DenunciaService service;

    @BeforeEach
    void configurar() {
        service = new DenunciaService(repository, ocorrencias, comentarios, provedorDeAtor,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ocorrencias.conteudo(ocorrenciaId)).thenReturn(ocorrencia(autor, false));
        lenient().when(comentarios.conteudo(comentarioId)).thenReturn(new ConteudoModeravel(TipoDeConteudo.COMENTARIO,
                comentarioId, ocorrenciaId, SAO_PAULO, autor, "Comentário ofensivo", false, StatusOcorrencia.ABERTA));
    }

    @Nested
    class NovaDenuncia {

        @Test
        void denunciaOcorrenciaNaCidadeDoConteudo() {
            Denuncia d = service.denunciar(denunciante, Alvo.ocorrencia(ocorrenciaId), Motivo.SPAM, "  ");

            assertThat(d.getTipoConteudo()).isEqualTo(TipoConteudo.OCORRENCIA);
            assertThat(d.getOcorrenciaId()).isEqualTo(ocorrenciaId);
            assertThat(d.getComentarioId()).isNull();
            assertThat(d.getCidadeId()).isEqualTo(SAO_PAULO);
            assertThat(d.getDescricao()).as("descrição em branco vira nula").isNull();
            assertThat(d.getStatus()).isEqualTo(Denuncia.Status.PENDENTE);
            verify(provedorDeAtor).obterParticipante(denunciante);
        }

        @Test
        void denunciaComentarioConsultaOModuloDeComentarios() {
            Denuncia d = service.denunciar(denunciante, Alvo.comentario(comentarioId), Motivo.OFENSA, " Xingamento ");

            assertThat(d.getTipoConteudo()).isEqualTo(TipoConteudo.COMENTARIO);
            assertThat(d.getComentarioId()).isEqualTo(comentarioId);
            assertThat(d.getOcorrenciaId()).isNull();
            assertThat(d.getDescricao()).isEqualTo("Xingamento");
            verify(ocorrencias, never()).conteudo(any());
        }

        @Test
        void suspensoNaoDenuncia() {
            when(provedorDeAtor.obterParticipante(denunciante))
                    .thenThrow(new AcessoNegadoException("USUARIO_SUSPENSO", "Conta suspensa"));

            assertThatThrownBy(() -> service.denunciar(denunciante, Alvo.ocorrencia(ocorrenciaId), Motivo.SPAM, null))
                    .hasFieldOrPropertyWithValue("codigo", "USUARIO_SUSPENSO");
            verify(repository, never()).save(any());
        }

        @Test
        void conteudoRemovidoNaoPodeSerDenunciado() {
            when(ocorrencias.conteudo(ocorrenciaId)).thenReturn(ocorrencia(autor, true));

            assertThatThrownBy(() -> service.denunciar(denunciante, Alvo.ocorrencia(ocorrenciaId), Motivo.SPAM, null))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "CONTEUDO_NAO_ENCONTRADO");
        }

        @Test
        void ninguemDenunciaOProprioConteudo() {
            assertThatThrownBy(() -> service.denunciar(autor, Alvo.ocorrencia(ocorrenciaId), Motivo.SPAM, null))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasFieldOrPropertyWithValue("codigo", "DENUNCIA_PROPRIA");
        }

        @Test
        void motivoOutroExigeDescricao() {
            assertThatThrownBy(() -> service.denunciar(denunciante, Alvo.ocorrencia(ocorrenciaId), Motivo.OUTRO, " "))
                    .hasFieldOrPropertyWithValue("codigo", "DESCRICAO_OBRIGATORIA");
        }

        @Test
        void umaDenunciaPorUsuarioPorConteudo() {
            when(repository.existeDoUsuario(denunciante, null, comentarioId, null)).thenReturn(true);

            assertThatThrownBy(() -> service.denunciar(denunciante, Alvo.comentario(comentarioId), Motivo.SPAM, null))
                    .isInstanceOf(ConflitoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "DENUNCIA_EXISTENTE");
        }

        @Test
        void conteudoDeAutorAnonimizadoAindaPodeSerDenunciado() {
            when(ocorrencias.conteudo(ocorrenciaId)).thenReturn(ocorrencia(null, false));

            assertThat(service.denunciar(denunciante, Alvo.ocorrencia(ocorrenciaId), Motivo.SPAM, null)).isNotNull();
        }
    }

    @Nested
    class Alteracao {

        private final UUID denunciaId = UUID.randomUUID();
        private Denuncia existente;

        @BeforeEach
        void criarDenuncia() {
            existente = new Denuncia(denunciante, Alvo.ocorrencia(ocorrenciaId), SAO_PAULO, Motivo.SPAM, null,
                    AGORA.minusSeconds(60));
            lenient().when(repository.findById(denunciaId)).thenReturn(Optional.of(existente));
        }

        @Test
        void trocaMotivoEAlvoIgnorandoAPropriaDenunciaNaChecagemDeDuplicidade() {
            Denuncia d = service.alterar(denunciaId, denunciante, Alvo.comentario(comentarioId), Motivo.OFENSA, "Ofensa");

            assertThat(d.getTipoConteudo()).isEqualTo(TipoConteudo.COMENTARIO);
            assertThat(d.getComentarioId()).isEqualTo(comentarioId);
            assertThat(d.getOcorrenciaId()).isNull();
            assertThat(d.getMotivo()).isEqualTo(Motivo.OFENSA);
            assertThat(d.getAtualizadaEm()).isEqualTo(AGORA);
            verify(repository).existeDoUsuario(denunciante, null, comentarioId, denunciaId);
        }

        @Test
        void inexistente() {
            UUID outra = UUID.randomUUID();
            when(repository.findById(outra)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.alterar(outra, denunciante, Alvo.ocorrencia(ocorrenciaId), Motivo.SPAM, null))
                    .hasFieldOrPropertyWithValue("codigo", "DENUNCIA_NAO_ENCONTRADA");
        }

        @Test
        void soODenuncianteAlteraEIssoVemAntesDeConsultarOConteudo() {
            UUID outro = UUID.randomUUID();

            assertThatThrownBy(() -> service.alterar(denunciaId, outro, Alvo.ocorrencia(ocorrenciaId), Motivo.SPAM, null))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "SOMENTE_DENUNCIANTE");
            verifyNoInteractions(ocorrencias, comentarios);
        }

        @Test
        void denunciaJaAnalisadaNaoMuda() {
            existente.resolver(Denuncia.Status.IMPROCEDENTE, UUID.randomUUID(), AGORA);

            assertThatThrownBy(() -> service.alterar(denunciaId, denunciante, Alvo.ocorrencia(ocorrenciaId),
                    Motivo.OFENSA, null))
                    .isInstanceOf(ConflitoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "DENUNCIA_JA_ANALISADA");
            assertThat(existente.getMotivo()).isEqualTo(Motivo.SPAM);
        }

        @Test
        void novoAlvoJaDenunciadoEhConflito() {
            when(repository.existeDoUsuario(denunciante, null, comentarioId, denunciaId)).thenReturn(true);

            assertThatThrownBy(() -> service.alterar(denunciaId, denunciante, Alvo.comentario(comentarioId),
                    Motivo.SPAM, null))
                    .hasFieldOrPropertyWithValue("codigo", "DENUNCIA_EXISTENTE");
            assertThat(existente.getOcorrenciaId()).isEqualTo(ocorrenciaId);
        }
    }

    @Test
    void guardaAsDuasChavesDoAlvoNaEntidade() {
        service.denunciar(denunciante, Alvo.ocorrencia(ocorrenciaId), Motivo.LOCAL_ERRADO, null);

        ArgumentCaptor<Denuncia> salva = ArgumentCaptor.forClass(Denuncia.class);
        verify(repository).save(salva.capture());
        assertThat(salva.getValue().alvo()).isEqualTo(Alvo.ocorrencia(ocorrenciaId));
    }

    private ConteudoModeravel ocorrencia(UUID autorId, boolean removida) {
        return new ConteudoModeravel(TipoDeConteudo.OCORRENCIA, ocorrenciaId, ocorrenciaId, SAO_PAULO, autorId,
                "Buraco", removida, StatusOcorrencia.ABERTA);
    }
}
