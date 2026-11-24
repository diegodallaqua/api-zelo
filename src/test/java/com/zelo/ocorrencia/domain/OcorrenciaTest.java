package com.zelo.ocorrencia.domain;

import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.ocorrencia.domain.HistoricoStatus.Papel;
import com.zelo.ocorrencia.domain.HistoricoStatus.TipoEvento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ciclo de vida da ocorrência, sem Spring nem banco. */
class OcorrenciaTest {

    private static final Integer CIDADE = 3550308;
    private static final Integer OUTRA_CIDADE = 3304557;
    private static final Duration PRAZO = Duration.ofDays(15);

    private final Instant t0 = Instant.parse("2026-11-01T12:00:00Z");
    private final UUID autor = UUID.randomUUID();
    private final UUID categoria = UUID.randomUUID();

    private final Ator gestor = new Ator(UUID.randomUUID(), Perfil.GESTOR, CIDADE);
    private final Ator moderador = new Ator(UUID.randomUUID(), Perfil.MODERADOR, CIDADE);
    private final Ator admin = new Ator(UUID.randomUUID(), Perfil.ADMIN, null);

    private Ocorrencia ocorrencia;

    @BeforeEach
    void criar() {
        ocorrencia = Ocorrencia.registrar(autor, categoria, CIDADE, "  Buraco grande na esquina  ", -23.5, -46.6,
                false, t0);
    }

    @Test
    @DisplayName("nasce Aberta, com histórico de criação e evento")
    void nasceAberta() {
        assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.ABERTA);
        assertThat(ocorrencia.getDescricao()).isEqualTo("Buraco grande na esquina");
        assertThat(ocorrencia.getHistorico()).singleElement().satisfies(h -> {
            assertThat(h.getTipoEvento()).isEqualTo(TipoEvento.CRIACAO);
            assertThat(h.getPapelAutor()).isEqualTo(Papel.AUTOR);
            assertThat(h.getAutorId()).as("ações do autor não guardam o id no histórico").isNull();
        });
    }

    @Nested
    class TransicoesDaEquipe {

        @Test
        void gestorAtendeEResolve() {
            ocorrencia.iniciarAtendimento(gestor, "Equipe enviada", t0.plusSeconds(60));
            ocorrencia.resolver(gestor, null, t0.plusSeconds(120));

            assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.RESOLVIDA);
            assertThat(ocorrencia.getResolvidaEm()).isEqualTo(t0.plusSeconds(120));
            assertThat(ocorrencia.getHistorico()).hasSize(3);
            assertThat(ocorrencia.getHistorico().get(1).getAutorId()).isEqualTo(gestor.id());
            assertThat(ocorrencia.getHistorico().get(1).getMotivo()).isEqualTo("Equipe enviada");
        }

        @Test
        void cidadaoNaoAlteraStatus() {
            Ator cidadao = Ator.cidadao(autor);
            assertThatThrownBy(() -> ocorrencia.resolver(cidadao, null, t0))
                    .isInstanceOf(TransicaoDeStatusInvalidaException.class);
            assertThatThrownBy(() -> ocorrencia.iniciarAtendimento(cidadao, null, t0))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        void naoPulaEtapas() {
            assertThatThrownBy(() -> ocorrencia.resolver(gestor, null, t0))
                    .isInstanceOf(TransicaoDeStatusInvalidaException.class)
                    .hasMessageContaining("ABERTA").hasMessageContaining("RESOLVIDA");
        }

        @Test
        void moderadorNaoIniciaAtendimento() {
            assertThatThrownBy(() -> ocorrencia.iniciarAtendimento(moderador, null, t0))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        void gestorDeOutraCidadeNaoAtua() {
            Ator deFora = new Ator(UUID.randomUUID(), Perfil.GESTOR, OUTRA_CIDADE);
            assertThatThrownBy(() -> ocorrencia.iniciarAtendimento(deFora, null, t0))
                    .isInstanceOf(AcessoNegadoException.class)
                    .extracting("codigo").isEqualTo("FORA_DA_AREA_DE_ATUACAO");
        }

        @Test
        void gestorAtuaEmQualquerCategoriaDaSuaCidade() {
            Ocorrencia outraCategoria = Ocorrencia.registrar(autor, UUID.randomUUID(), CIDADE, "Poste apagado na praça",
                    -23.5, -46.6, false, t0);
            outraCategoria.iniciarAtendimento(gestor, null, t0);
            assertThat(outraCategoria.getStatus()).isEqualTo(StatusOcorrencia.EM_ANDAMENTO);
        }

        @Test
        void rejeicaoExigeMotivo() {
            assertThatThrownBy(() -> ocorrencia.rejeitar(moderador, "  ", t0))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .extracting("codigo").isEqualTo("MOTIVO_OBRIGATORIO");
            ocorrencia.rejeitar(moderador, "Não é via pública", t0);
            assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.REJEITADA);
        }

        @Test
        void interromperAtendimentoExigeMotivo() {
            ocorrencia.iniciarAtendimento(gestor, null, t0);
            assertThatThrownBy(() -> ocorrencia.transicionar(StatusOcorrencia.ABERTA, gestor, null, t0))
                    .isInstanceOf(RegraDeNegocioException.class);
            ocorrencia.transicionar(StatusOcorrencia.ABERTA, gestor, "Aguardando material", t0);
            assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.ABERTA);
        }

        @Test
        void duplicadaSoPorMesclagem() {
            assertThatThrownBy(() -> ocorrencia.transicionar(StatusOcorrencia.DUPLICADA, moderador, "x", t0))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .extracting("codigo").isEqualTo("USE_MESCLAGEM");
        }

        @Test
        void arquivadaNaoPodeSerReaberta() {
            ocorrencia.arquivarPorInatividade(t0);
            assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.ARQUIVADA);
            assertThatThrownBy(() -> ocorrencia.transicionar(StatusOcorrencia.ABERTA, admin, "reabrir", t0))
                    .isInstanceOf(TransicaoDeStatusInvalidaException.class);
        }

        @Test
        void adminExecutaQualquerTransicaoDaMatriz() {
            ocorrencia.iniciarAtendimento(admin, null, t0);
            ocorrencia.resolver(admin, null, t0);
            assertThat(ocorrencia.getHistorico().get(2).getPapelAutor()).isEqualTo(Papel.ADMIN);
        }
    }

    @Nested
    class ConfirmacaoEContestacao {

        @BeforeEach
        void resolver() {
            ocorrencia.iniciarAtendimento(gestor, null, t0);
            ocorrencia.resolver(gestor, null, t0);
        }

        @Test
        void contestacaoNoPrazoReabre() {
            ocorrencia.contestarResolucao(autor, "Buraco continua lá", PRAZO, t0.plus(Duration.ofDays(3)));

            assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.ABERTA);
            assertThat(ocorrencia.getResolvidaEm()).isNull();
            assertThat(ocorrencia.getHistorico()).last()
                    .extracting(HistoricoStatus::getTipoEvento).isEqualTo(TipoEvento.CONTESTACAO);
        }

        @Test
        void contestacaoForaDoPrazoRecusada() {
            assertThatThrownBy(() -> ocorrencia.contestarResolucao(autor, "Continua", PRAZO, t0.plus(Duration.ofDays(16))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .extracting("codigo").isEqualTo("PRAZO_DE_CONTESTACAO_ENCERRADO");
        }

        @Test
        void soOAutorContesta() {
            assertThatThrownBy(() -> ocorrencia.contestarResolucao(UUID.randomUUID(), "x", PRAZO, t0))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        void contestacaoExigeMotivo() {
            assertThatThrownBy(() -> ocorrencia.contestarResolucao(autor, "", PRAZO, t0))
                    .isInstanceOf(RegraDeNegocioException.class);
        }

        @Test
        void confirmacaoEncerraDeVez() {
            ocorrencia.confirmarResolucao(autor, PRAZO, t0.plus(Duration.ofDays(1)));

            assertThat(ocorrencia.getResolucaoConfirmadaEm()).isNotNull();
            assertThat(ocorrencia.prazoDeContestacao(PRAZO)).isEmpty();
            assertThatThrownBy(() -> ocorrencia.contestarResolucao(autor, "mudei de ideia", PRAZO, t0))
                    .isInstanceOf(ConflitoException.class);
            assertThatThrownBy(() -> ocorrencia.transicionar(StatusOcorrencia.ABERTA, admin, "reabrir", t0))
                    .isInstanceOf(ConflitoException.class)
                    .extracting("codigo").isEqualTo("OCORRENCIA_ENCERRADA");
        }

        @Test
        void prazoDeContestacaoInformado() {
            assertThat(ocorrencia.prazoDeContestacao(PRAZO)).contains(t0.plus(PRAZO));
        }
    }

    @Nested
    class AcoesDoAutor {

        @Test
        void editaEnquantoAberta() {
            UUID novaCategoria = UUID.randomUUID();
            ocorrencia.editar(autor, "Buraco maior agora", novaCategoria, t0);
            assertThat(ocorrencia.getDescricao()).isEqualTo("Buraco maior agora");
            assertThat(ocorrencia.getCategoriaId()).isEqualTo(novaCategoria);
        }

        @Test
        void naoEditaEmAndamento() {
            ocorrencia.iniciarAtendimento(gestor, null, t0);
            assertThatThrownBy(() -> ocorrencia.editar(autor, "Nova descrição", null, t0))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .extracting("codigo").isEqualTo("OCORRENCIA_NAO_EDITAVEL");
        }

        @Test
        void outroUsuarioNaoEdita() {
            assertThatThrownBy(() -> ocorrencia.editar(UUID.randomUUID(), "Nova descrição", null, t0))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        void exclusaoEscondeAOcorrencia() {
            ocorrencia.excluirPeloAutor(autor, t0);
            assertThat(ocorrencia.estaRemovida()).isTrue();
            assertThatThrownBy(ocorrencia::exigirNaoRemovida).isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        void fotosRespeitamMinimoEMaximo() {
            UUID f1 = UUID.randomUUID();
            ocorrencia.adicionarFoto(f1, "k1", "image/jpeg", 10, 100, 100, 2, t0);
            ocorrencia.adicionarFoto(UUID.randomUUID(), "k2", "image/jpeg", 10, 100, 100, 2, t0);
            assertThatThrownBy(() -> ocorrencia.adicionarFoto(UUID.randomUUID(), "k3", "image/jpeg", 10, 100, 100, 2, t0))
                    .isInstanceOf(RegraDeNegocioException.class);

            ocorrencia.removerFoto(autor, f1, 1, t0);
            assertThat(ocorrencia.getFotos()).hasSize(1);
            UUID ultima = ocorrencia.getFotos().get(0).getId();
            assertThatThrownBy(() -> ocorrencia.removerFoto(autor, ultima, 1, t0))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .extracting("codigo").isEqualTo("FOTO_OBRIGATORIA");
        }

        @Test
        void naoCurteAPropria() {
            assertThatThrownBy(() -> ocorrencia.exigirPodeReceberCurtidaDe(autor))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .extracting("codigo").isEqualTo("CURTIDA_PROPRIA");
        }

        @Test
        void naoCurteResolvida() {
            ocorrencia.iniciarAtendimento(gestor, null, t0);
            ocorrencia.resolver(gestor, null, t0);
            assertThatThrownBy(() -> ocorrencia.exigirPodeReceberCurtidaDe(UUID.randomUUID()))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .extracting("codigo").isEqualTo("OCORRENCIA_NAO_ATIVA");
        }
    }

    @Nested
    class Mesclagem {

        private Ocorrencia principal;

        @BeforeEach
        void criarPrincipal() {
            principal = Ocorrencia.registrar(UUID.randomUUID(), categoria, CIDADE, "Mesmo buraco já relatado", -23.5,
                    -46.6, false, t0);
        }

        @Test
        void moderadorMescla() {
            ocorrencia.marcarComoDuplicada(principal, moderador, "Mesmo local", t0);
            assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.DUPLICADA);
            assertThat(ocorrencia.getOcorrenciaPrincipalId()).isEqualTo(principal.getId());
        }

        @Test
        void gestorNaoMescla() {
            assertThatThrownBy(() -> ocorrencia.marcarComoDuplicada(principal, gestor, "x", t0))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        void naoMesclaComOutraCidade() {
            Ocorrencia longe = Ocorrencia.registrar(UUID.randomUUID(), categoria, OUTRA_CIDADE, "Outra cidade aqui",
                    -22.9, -43.2, false, t0);
            assertThatThrownBy(() -> ocorrencia.marcarComoDuplicada(longe, admin, "x", t0))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .extracting("codigo").isEqualTo("CIDADES_DIFERENTES");
        }

        @Test
        void principalPrecisaEstarAtiva() {
            principal.rejeitar(moderador, "Inválida", t0);
            assertThatThrownBy(() -> ocorrencia.marcarComoDuplicada(principal, moderador, "x", t0))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .extracting("codigo").isEqualTo("PRINCIPAL_INATIVA");
        }

        @Test
        void naoMesclaConsigoMesma() {
            assertThatThrownBy(() -> ocorrencia.marcarComoDuplicada(ocorrencia, moderador, "x", t0))
                    .isInstanceOf(RegraDeNegocioException.class);
        }
    }

    @Nested
    class Rotinas {

        @Test
        void avisoEArquivamento() {
            ocorrencia.avisarArquivamento(t0);
            assertThat(ocorrencia.getAvisoArquivamentoEm()).isEqualTo(t0);

            ocorrencia.arquivarPorInatividade(t0.plus(Duration.ofDays(30)));
            assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.ARQUIVADA);
            assertThat(ocorrencia.getHistorico()).last()
                    .extracting(HistoricoStatus::getPapelAutor).isEqualTo(Papel.SISTEMA);
        }

        @Test
        void atividadeCancelaOAviso() {
            ocorrencia.avisarArquivamento(t0);
            ocorrencia.editar(autor, "Atualizando a descrição", null, t0.plusSeconds(10));
            assertThat(ocorrencia.getAvisoArquivamentoEm()).isNull();
        }

        @Test
        void soArquivaAbertas() {
            ocorrencia.iniciarAtendimento(gestor, null, t0);
            ocorrencia.arquivarPorInatividade(t0);
            assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.EM_ANDAMENTO);
        }
    }
}
