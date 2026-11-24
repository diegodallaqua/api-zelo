package com.zelo.ocorrencia.application;

import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.ocorrencia.application.Visoes.CurtidaVisao;
import com.zelo.ocorrencia.domain.CalculadoraDePrioridade;
import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.domain.PrioridadePorCurtidasEDiasEmAberto;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia;
import com.zelo.ocorrencia.infra.OcorrenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurtidaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");
    private static final int SAO_PAULO = 3550308;

    @Mock OcorrenciaRepository repository;
    @Mock ConsultasDeOcorrencia consultas;
    @Mock ProvedorDeAtor provedorDeAtor;

    private final CalculadoraDePrioridade calculadora = new PrioridadePorCurtidasEDiasEmAberto();
    private final UUID autor = UUID.randomUUID();
    private final UUID apoiador = UUID.randomUUID();
    private CurtidaService service;
    private Ocorrencia ocorrencia;

    @BeforeEach
    void configurar() {
        service = new CurtidaService(repository, consultas, calculadora, provedorDeAtor,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        ocorrencia = Ocorrencia.registrar(autor, UUID.randomUUID(), SAO_PAULO, "Buraco", -23.55, -46.63, false,
                AGORA.minus(Duration.ofDays(10)));
        lenient().when(repository.findById(ocorrencia.getId())).thenReturn(Optional.of(ocorrencia));
    }

    @Test
    void curtirAtualizaContadorEPrioridadeNoBanco() {
        when(consultas.inserirCurtida(ocorrencia.getId(), apoiador, AGORA)).thenReturn(true);
        when(consultas.sincronizarCurtidas(ocorrencia.getId(), AGORA)).thenReturn(3);

        CurtidaVisao visao = service.curtir(ocorrencia.getId(), apoiador);

        assertThat(visao).isEqualTo(new CurtidaVisao(ocorrencia.getId(), 3, true));
        verify(consultas).atualizarPrioridade(ocorrencia.getId(), calculadora.calcular(3, ocorrencia.getCriadaEm(), AGORA));
    }

    @Test
    void segundaCurtidaDoMesmoUsuarioEhConflito() {
        when(consultas.inserirCurtida(ocorrencia.getId(), apoiador, AGORA)).thenReturn(false);

        assertThatThrownBy(() -> service.curtir(ocorrencia.getId(), apoiador))
                .isInstanceOf(ConflitoException.class)
                .hasFieldOrPropertyWithValue("codigo", "CURTIDA_EXISTENTE");
        verify(consultas, never()).sincronizarCurtidas(any(), any());
        verify(consultas, never()).atualizarPrioridade(any(), anyInt());
    }

    @Test
    void autorNaoCurteAPropriaOcorrencia() {
        assertThatThrownBy(() -> service.curtir(ocorrencia.getId(), autor))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", "CURTIDA_PROPRIA");
        verify(consultas, never()).inserirCurtida(any(), any(), any());
    }

    @Test
    void ocorrenciaResolvidaNaoRecebeCurtidas() {
        Ator gestor = new Ator(UUID.randomUUID(), Perfil.GESTOR, SAO_PAULO);
        ocorrencia.iniciarAtendimento(gestor, null, AGORA.minusSeconds(120));
        ocorrencia.resolver(gestor, null, AGORA.minusSeconds(60));

        assertThatThrownBy(() -> service.curtir(ocorrencia.getId(), apoiador))
                .hasFieldOrPropertyWithValue("codigo", "OCORRENCIA_NAO_ATIVA");
    }

    @Test
    void suspensoNaoCurte() {
        when(provedorDeAtor.obterParticipante(apoiador)).thenThrow(new AcessoNegadoException("USUARIO_SUSPENSO", "x"));

        assertThatThrownBy(() -> service.curtir(ocorrencia.getId(), apoiador))
                .hasFieldOrPropertyWithValue("codigo", "USUARIO_SUSPENSO");
        verify(consultas, never()).inserirCurtida(any(), any(), any());
    }

    @Test
    void ocorrenciaInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.curtir(id, apoiador))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasFieldOrPropertyWithValue("codigo", "OCORRENCIA_NAO_ENCONTRADA");
    }

    @Test
    void descurtirRecalculaOsContadores() {
        when(consultas.removerCurtida(ocorrencia.getId(), apoiador)).thenReturn(true);
        when(consultas.sincronizarCurtidas(ocorrencia.getId(), AGORA)).thenReturn(0);

        CurtidaVisao visao = service.descurtir(ocorrencia.getId(), apoiador);

        assertThat(visao).isEqualTo(new CurtidaVisao(ocorrencia.getId(), 0, false));
        verify(consultas).atualizarPrioridade(ocorrencia.getId(), calculadora.calcular(0, ocorrencia.getCriadaEm(), AGORA));
        verify(provedorDeAtor).obter(apoiador);
        verify(provedorDeAtor, never()).obterParticipante(any());
    }

    @Test
    void descurtirSemTerCurtido() {
        when(consultas.removerCurtida(ocorrencia.getId(), apoiador)).thenReturn(false);

        assertThatThrownBy(() -> service.descurtir(ocorrencia.getId(), apoiador))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasFieldOrPropertyWithValue("codigo", "CURTIDA_NAO_ENCONTRADA");
    }
}
