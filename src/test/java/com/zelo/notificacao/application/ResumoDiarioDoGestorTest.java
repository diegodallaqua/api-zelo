package com.zelo.notificacao.application;

import com.zelo.notificacao.domain.Notificacao.Tipo;
import com.zelo.ocorrencia.application.ConsultaDeOcorrenciaService;
import com.zelo.usuario.application.UsuarioService;
import com.zelo.usuario.application.UsuarioService.Gestor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumoDiarioDoGestorTest {

    private static final Instant AGORA = Instant.parse("2026-11-23T10:00:00Z");
    private static final Instant ONTEM = Instant.parse("2026-11-22T10:00:00Z");

    @Mock UsuarioService usuarios;
    @Mock ConsultaDeOcorrenciaService ocorrencias;
    @Mock NotificacaoService notificacoes;

    @Test
    @SuppressWarnings("unchecked")
    void avisaSoQuemTemNovidadesEUmaFalhaNaoParaOsOutros() {
        Gestor saoPaulo = new Gestor(UUID.randomUUID(), 3550308);
        Gestor rio = new Gestor(UUID.randomUUID(), 3304557);
        Gestor comErro = new Gestor(UUID.randomUUID(), 3509502);
        when(usuarios.listarGestoresAtivos()).thenReturn(List.of(comErro, saoPaulo, rio));
        when(ocorrencias.contarNovas(3509502, ONTEM)).thenThrow(new IllegalStateException("consulta falhou"));
        when(ocorrencias.contarNovas(3550308, ONTEM)).thenReturn(3L);
        when(ocorrencias.contarNovas(3304557, ONTEM)).thenReturn(0L);

        new ResumoDiarioDoGestor(usuarios, ocorrencias, notificacoes, Clock.fixed(AGORA, ZoneOffset.UTC)).enviar();

        ArgumentCaptor<Function<UUID, String>> mensagem = ArgumentCaptor.forClass(Function.class);
        verify(notificacoes).notificar(eq(Set.of(saoPaulo.id())), eq(Tipo.RESUMO_GESTOR), eq("Resumo diário"),
                mensagem.capture(), isNull());
        assertThat(mensagem.getValue().apply(saoPaulo.id()))
                .isEqualTo("3 nova(s) ocorrência(s) na sua área nas últimas 24 horas.");
        verify(notificacoes, never()).notificar(eq(Set.of(rio.id())), any(), any(), any(), any());
        verify(notificacoes, never()).notificar(eq(Set.of(comErro.id())), any(), any(), any(), any());
    }
}
