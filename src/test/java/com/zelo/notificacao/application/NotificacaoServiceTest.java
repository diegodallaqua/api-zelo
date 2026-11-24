package com.zelo.notificacao.application;

import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.notificacao.domain.Notificacao;
import com.zelo.notificacao.domain.Notificador;
import com.zelo.notificacao.domain.Notificador.Canal;
import com.zelo.notificacao.domain.Notificador.Envio;
import com.zelo.notificacao.domain.PreferenciaNotificacao;
import com.zelo.notificacao.infra.NotificacaoRepository;
import com.zelo.notificacao.infra.PreferenciaNotificacaoRepository;
import com.zelo.notificacao.infra.SilenciamentoRepository;
import com.zelo.ocorrencia.application.CicloDeVidaService;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");

    @Mock NotificacaoRepository notificacoes;
    @Mock PreferenciaNotificacaoRepository preferencias;
    @Mock SilenciamentoRepository silenciamentos;
    @Mock CicloDeVidaService ocorrencias;
    @Mock Notificador notificador;

    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private final UUID caio = UUID.randomUUID();
    private final UUID ocorrenciaId = UUID.randomUUID();
    private NotificacaoService service;

    @BeforeEach
    void configurar() {
        service = new NotificacaoService(notificacoes, preferencias, silenciamentos, ocorrencias, notificador,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(preferencias.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class EnvioDeNotificacoes {

        @Test
        void quemSilenciouAOcorrenciaNaoRecebeNada() {
            when(silenciamentos.quemSilenciou(ocorrenciaId)).thenReturn(Set.of(bruno));
            when(preferencias.findAllById(any())).thenReturn(List.of());

            service.notificar(List.of(ana, bruno), Notificacao.Tipo.STATUS_ALTERADO, "Atualizada",
                    u -> "Mensagem para " + u, ocorrenciaId);

            ArgumentCaptor<Notificacao> salva = ArgumentCaptor.forClass(Notificacao.class);
            verify(notificacoes).save(salva.capture());
            assertThat(salva.getValue().getUsuarioId()).isEqualTo(ana);
            assertThat(salva.getValue().getMensagem()).isEqualTo("Mensagem para " + ana);
            assertThat(salva.getValue().getOcorrenciaId()).isEqualTo(ocorrenciaId);
            verify(notificador, times(1)).enviar(any());
        }

        @Test
        void cadaUmRecebePelosCanaisQueEscolheuEOPadraoEhSoPush() {
            when(silenciamentos.quemSilenciou(ocorrenciaId)).thenReturn(Set.of());
            when(preferencias.findAllById(any())).thenReturn(List.of(
                    new PreferenciaNotificacao(bruno, false, true, AGORA),
                    new PreferenciaNotificacao(caio, false, false, AGORA)));

            service.notificar(List.of(ana, bruno, caio), Notificacao.Tipo.STATUS_ALTERADO, "Título", u -> "Texto",
                    ocorrenciaId);

            ArgumentCaptor<Envio> envios = ArgumentCaptor.forClass(Envio.class);
            verify(notificador, times(3)).enviar(envios.capture());
            assertThat(envios.getAllValues()).extracting(Envio::usuarioId, Envio::canais).containsExactly(
                    org.assertj.core.groups.Tuple.tuple(ana, Set.of(Canal.PUSH)),
                    org.assertj.core.groups.Tuple.tuple(bruno, Set.of(Canal.EMAIL)),
                    org.assertj.core.groups.Tuple.tuple(caio, Set.of()));
            verify(notificacoes, times(3)).save(any());
        }

        @Test
        void notificacaoSemOcorrenciaNaoConsultaSilenciamentos() {
            when(preferencias.findAllById(any())).thenReturn(List.of());

            service.notificar(Set.of(ana), Notificacao.Tipo.CONTA_SUSPENSA, "Conta suspensa", u -> "x", null);

            verifyNoInteractions(silenciamentos);
            verify(notificacoes).save(any());
        }

        @Test
        void semDestinatariosNaoFazNada() {
            service.notificar(Set.of(), Notificacao.Tipo.RESUMO_GESTOR, "Resumo", u -> "x", null);

            verifyNoInteractions(notificacoes, preferencias, silenciamentos, notificador);
        }

        @Test
        void mensagemLongaEhCortada() {
            when(preferencias.findAllById(any())).thenReturn(List.of());

            service.notificar(Set.of(ana), Notificacao.Tipo.CONTEUDO_REMOVIDO, "Removido", u -> "a".repeat(1500), null);

            ArgumentCaptor<Notificacao> salva = ArgumentCaptor.forClass(Notificacao.class);
            verify(notificacoes).save(salva.capture());
            assertThat(salva.getValue().getMensagem()).hasSize(1000).endsWith("...");
        }
    }

    @Nested
    class Leitura {

        @Test
        void marcaComoLidaSoANotificacaoDoProprioUsuario() {
            Notificacao doBruno = new Notificacao(bruno, Notificacao.Tipo.STATUS_ALTERADO, "t", "m", null, AGORA);
            UUID id = UUID.randomUUID();
            when(notificacoes.findById(id)).thenReturn(Optional.of(doBruno));

            assertThatThrownBy(() -> service.marcarComoLida(id, ana))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "NOTIFICACAO_NAO_ENCONTRADA");
            assertThat(doBruno.getLidaEm()).isNull();

            service.marcarComoLida(id, bruno);
            assertThat(doBruno.getLidaEm()).isEqualTo(AGORA);
        }

        @Test
        void listaSoAsNaoLidasQuandoPedido() {
            service.listar(ana, true, org.springframework.data.domain.Pageable.unpaged());

            verify(notificacoes).findByUsuarioIdAndLidaEmIsNullOrderByCriadaEmDesc(any(), any());
            verify(notificacoes, never()).findByUsuarioIdOrderByCriadaEmDesc(any(), any());
        }
    }

    @Nested
    class Preferencias {

        @Test
        void semRegistroValeOPadrao() {
            when(preferencias.findById(ana)).thenReturn(Optional.empty());

            PreferenciaNotificacao p = service.preferencias(ana);

            assertThat(p.isPush()).isTrue();
            assertThat(p.isEmail()).isFalse();
            verify(preferencias, never()).save(any());
        }

        @Test
        void alterarCriaORegistroQuandoNaoExiste() {
            when(preferencias.findById(ana)).thenReturn(Optional.empty());

            PreferenciaNotificacao p = service.alterarPreferencias(ana, false, true);

            assertThat(p.canais()).containsExactly(Canal.EMAIL);
            assertThat(p.getAtualizadaEm()).isEqualTo(AGORA);
            verify(preferencias).save(p);
        }
    }

    @Nested
    class Silenciamento {

        @Test
        void silenciaOcorrenciaExistente() {
            when(ocorrencias.conteudo(ocorrenciaId)).thenReturn(conteudo(false));

            service.silenciar(ana, ocorrenciaId);

            verify(silenciamentos).silenciar(ana, ocorrenciaId, AGORA);
        }

        @Test
        void ocorrenciaRemovidaNaoPodeSerSilenciada() {
            when(ocorrencias.conteudo(ocorrenciaId)).thenReturn(conteudo(true));

            assertThatThrownBy(() -> service.silenciar(ana, ocorrenciaId))
                    .hasFieldOrPropertyWithValue("codigo", "OCORRENCIA_NAO_ENCONTRADA");
            verify(silenciamentos, never()).silenciar(any(), any(), any());
        }

        private ConteudoModeravel conteudo(boolean removido) {
            return new ConteudoModeravel(TipoDeConteudo.OCORRENCIA, ocorrenciaId, ocorrenciaId, 3550308, bruno, "x",
                    removido, StatusOcorrencia.ABERTA);
        }
    }

    @Test
    void exclusaoDaContaApagaTudoDoUsuario() {
        service.excluirDadosDoUsuario(ana);

        verify(notificacoes).excluirDoUsuario(ana);
        verify(silenciamentos).excluirDoUsuario(ana);
        verify(preferencias).deleteById(ana);
    }
}
