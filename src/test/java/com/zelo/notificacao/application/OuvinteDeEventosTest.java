package com.zelo.notificacao.application;

import com.zelo.notificacao.domain.Notificacao.Tipo;
import com.zelo.ocorrencia.application.ConsultaDeOcorrenciaService;
import com.zelo.ocorrencia.domain.HistoricoStatus.TipoEvento;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.ConteudoRemovidoPelaModeracao;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.OcorrenciaMesclada;
import com.zelo.ocorrencia.domain.evento.EventosDeOcorrencia.StatusAlterado;
import com.zelo.usuario.domain.EventosDeUsuario.ContaExcluida;
import com.zelo.usuario.domain.EventosDeUsuario.UsuarioSuspenso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OuvinteDeEventosTest {

    @Mock NotificacaoService notificacoes;
    @Mock ConsultaDeOcorrenciaService ocorrencias;

    @Captor ArgumentCaptor<Collection<UUID>> destinatarios;
    @Captor ArgumentCaptor<Function<UUID, String>> mensagem;

    private final UUID ocorrenciaId = UUID.randomUUID();
    private final UUID autor = UUID.randomUUID();
    private final UUID apoiador = UUID.randomUUID();
    private OuvinteDeEventos ouvinte;

    @BeforeEach
    void configurar() {
        ouvinte = new OuvinteDeEventos(notificacoes, ocorrencias);
    }

    @Test
    void mudancaDeStatusAvisaAutorEApoiadoresComTextosDiferentes() {
        when(ocorrencias.interessados(ocorrenciaId)).thenReturn(new LinkedHashSet<>(List.of(autor, apoiador)));

        ouvinte.aoAlterarStatus(new StatusAlterado(ocorrenciaId, autor, StatusOcorrencia.ABERTA,
                StatusOcorrencia.EM_ANDAMENTO, TipoEvento.TRANSICAO, "Equipe enviada"));

        verify(notificacoes).notificar(destinatarios.capture(), eq(Tipo.STATUS_ALTERADO), eq("Ocorrência atualizada"),
                mensagem.capture(), eq(ocorrenciaId));
        assertThat(destinatarios.getValue()).containsExactly(autor, apoiador);
        assertThat(mensagem.getValue().apply(autor))
                .isEqualTo("Sua ocorrência mudou de \"Aberta\" para \"Em andamento\". Motivo: Equipe enviada");
        assertThat(mensagem.getValue().apply(apoiador))
                .isEqualTo("Uma ocorrência que você apoia mudou de \"Aberta\" para \"Em andamento\". Motivo: Equipe enviada");
    }

    @Test
    void contestacaoNaoAvisaOProprioAutorQueContestou() {
        when(ocorrencias.interessados(ocorrenciaId)).thenReturn(Set.of(autor, apoiador));

        ouvinte.aoAlterarStatus(new StatusAlterado(ocorrenciaId, autor, StatusOcorrencia.RESOLVIDA,
                StatusOcorrencia.ABERTA, TipoEvento.CONTESTACAO, "Não foi consertado"));

        verify(notificacoes).notificar(destinatarios.capture(), any(), anyString(), any(), any());
        assertThat(destinatarios.getValue()).containsExactly(apoiador);
    }

    @Test
    void falhaAoNotificarNaoPropaga() {
        when(ocorrencias.interessados(ocorrenciaId)).thenReturn(Set.of(apoiador));
        doThrow(new IllegalStateException("banco fora")).when(notificacoes)
                .notificar(any(), any(), anyString(), any(), any());

        assertThatCode(() -> ouvinte.aoAlterarStatus(new StatusAlterado(ocorrenciaId, autor, StatusOcorrencia.ABERTA,
                StatusOcorrencia.RESOLVIDA, TipoEvento.TRANSICAO, null))).doesNotThrowAnyException();
    }

    @Test
    void remocaoDeComentarioAvisaOAutorComOMotivo() {
        ouvinte.aoRemoverConteudo(new ConteudoRemovidoPelaModeracao("COMENTARIO", UUID.randomUUID(), ocorrenciaId,
                autor, "Linguagem ofensiva"));

        verify(notificacoes).notificar(eq(Set.of(autor)), eq(Tipo.CONTEUDO_REMOVIDO), anyString(), mensagem.capture(),
                isNull());
        assertThat(mensagem.getValue().apply(autor))
                .isEqualTo("Seu comentário foi removido pela moderação. Motivo: Linguagem ofensiva");
    }

    @Test
    void conteudoDeContaExcluidaNaoGeraNotificacao() {
        ouvinte.aoRemoverConteudo(new ConteudoRemovidoPelaModeracao("OCORRENCIA", ocorrenciaId, ocorrenciaId, null, "x"));
        ouvinte.aoMesclar(new OcorrenciaMesclada(ocorrenciaId, UUID.randomUUID(), null));

        verifyNoInteractions(notificacoes);
    }

    @Test
    void mesclagemApontaParaAPrincipal() {
        UUID principal = UUID.randomUUID();

        ouvinte.aoMesclar(new OcorrenciaMesclada(ocorrenciaId, principal, autor));

        verify(notificacoes).notificar(eq(Set.of(autor)), eq(Tipo.OCORRENCIA_MESCLADA), anyString(), any(),
                eq(principal));
    }

    @Test
    void suspensaoTemporariaMostraADataEmSaoPaulo() {
        // 02:00 UTC do dia 10 ainda é dia 9 em São Paulo.
        ouvinte.aoSuspender(new UsuarioSuspenso(autor, Instant.parse("2026-12-10T02:00:00Z"), false, "Spam"));

        verify(notificacoes).notificar(eq(Set.of(autor)), eq(Tipo.CONTA_SUSPENSA), anyString(), mensagem.capture(),
                isNull());
        assertThat(mensagem.getValue().apply(autor)).isEqualTo("Sua conta foi suspensa até 09/12/2026. Motivo: Spam");
    }

    @Test
    void suspensaoPermanente() {
        ouvinte.aoSuspender(new UsuarioSuspenso(autor, null, true, "Reincidência"));

        verify(notificacoes).notificar(any(), any(), anyString(), mensagem.capture(), any());
        assertThat(mensagem.getValue().apply(autor)).contains("permanentemente");
    }

    @Test
    void exclusaoDeContaApagaAsNotificacoes() {
        ouvinte.aoExcluirConta(new ContaExcluida(autor));

        verify(notificacoes).excluirDadosDoUsuario(autor);
    }

    @ParameterizedTest
    @EnumSource(StatusOcorrencia.class)
    void todoStatusTemTextoLegivel(StatusOcorrencia status) {
        assertThat(OuvinteDeEventos.texto(status)).isNotBlank().doesNotContain("_");
    }
}
