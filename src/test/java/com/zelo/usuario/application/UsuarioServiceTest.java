package com.zelo.usuario.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.NaoAutenticadoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.usuario.application.UsuarioService.Gestor;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.EventosDeUsuario.UsuarioSuspenso;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.infra.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");
    private static final int SAO_PAULO = 3550308;

    @Mock UsuarioRepository repository;
    @Mock AuditoriaService auditoria;
    @Mock ApplicationEventPublisher eventos;

    private UsuarioService service;

    @BeforeEach
    void configurar() {
        service = new UsuarioService(repository, auditoria, eventos, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Nested
    class IdentificacaoDoAtor {

        @Test
        void atorDaEquipeTrazACidadeDoEndereco() {
            Usuario gestor = registrar(Perfil.GESTOR);

            assertThat(service.obter(gestor.getId())).isEqualTo(new Ator(gestor.getId(), Perfil.GESTOR, SAO_PAULO));
        }

        @Test
        void contaExcluidaPrecisaFazerLoginDeNovo() {
            Usuario ana = registrar(Perfil.CIDADAO);
            ana.anonimizar(AGORA);

            assertThatThrownBy(() -> service.obter(ana.getId()))
                    .isInstanceOf(NaoAutenticadoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "CONTA_INDISPONIVEL");
        }

        @Test
        void suspensoLeMasNaoParticipa() {
            Usuario ana = registrar(Perfil.CIDADAO);
            ana.suspender(Instant.parse("2026-12-01T15:00:00Z"), false, "Spam", AGORA);

            assertThat(service.obter(ana.getId()).id()).isEqualTo(ana.getId());
            assertThatThrownBy(() -> service.obterParticipante(ana.getId()))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "USUARIO_SUSPENSO")
                    .hasMessageContaining("até 01/12/2026 às 12:00");
        }

        @Test
        void suspensaoPermanenteApareceNaMensagem() {
            Usuario ana = registrar(Perfil.CIDADAO);
            ana.suspender(null, true, "Reincidência", AGORA);

            assertThatThrownBy(() -> service.obterParticipante(ana.getId()))
                    .hasMessageContaining("permanentemente");
        }

        @Test
        void suspensaoVencidaNaoBloqueia() {
            Usuario ana = registrar(Perfil.CIDADAO);
            ana.suspender(AGORA.plusSeconds(60), false, "Spam", AGORA.minus(Duration.ofDays(1)));
            Clock depois = Clock.fixed(AGORA.plusSeconds(61), ZoneOffset.UTC);

            Ator ator = new UsuarioService(repository, auditoria, eventos, depois).obterParticipante(ana.getId());

            assertThat(ator.perfil()).isEqualTo(Perfil.CIDADAO);
        }
    }

    @Nested
    class Suspensao {

        @Test
        void moderadorSuspendeCidadaoAuditaEAvisa() {
            Usuario ana = registrar(Perfil.CIDADAO);
            Ator moderador = new Ator(UUID.randomUUID(), Perfil.MODERADOR, SAO_PAULO);
            Instant ate = AGORA.plus(Duration.ofDays(7));

            service.suspender(ana.getId(), ate, false, "Spam", moderador);

            assertThat(ana.estaSuspenso(AGORA)).isTrue();
            verify(auditoria).registrar(eq(moderador), eq("USUARIO_SUSPENSO"), eq("USUARIO"), eq(ana.getId()), anyMap());
            verify(eventos).publishEvent(new UsuarioSuspenso(ana.getId(), ate, false, "Spam"));
        }

        @Test
        void ninguemSuspendeASiMesmo() {
            Usuario mod = registrar(Perfil.MODERADOR);
            Ator ator = new Ator(mod.getId(), Perfil.MODERADOR, SAO_PAULO);

            assertThatThrownBy(() -> service.suspender(mod.getId(), null, true, "x", ator))
                    .hasFieldOrPropertyWithValue("codigo", "OPERACAO_SOBRE_SI_MESMO");
        }

        @Test
        void administradorNaoPodeSerSuspenso() {
            Usuario admin = registrar(Perfil.ADMIN);
            Ator outroAdmin = new Ator(UUID.randomUUID(), Perfil.ADMIN, null);

            assertThatThrownBy(() -> service.suspender(admin.getId(), null, true, "x", outroAdmin))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "ALVO_PROTEGIDO");
        }

        @Test
        void moderadorSoSuspendeCidadaos() {
            Usuario gestor = registrar(Perfil.GESTOR);
            Ator moderador = new Ator(UUID.randomUUID(), Perfil.MODERADOR, SAO_PAULO);

            assertThatThrownBy(() -> service.suspender(gestor.getId(), null, true, "x", moderador))
                    .hasFieldOrPropertyWithValue("codigo", "ALVO_PROTEGIDO");
            verify(eventos, never()).publishEvent(any(Object.class));
        }

        @Test
        void administradorSuspendeMembroDaEquipe() {
            Usuario gestor = registrar(Perfil.GESTOR);

            service.suspender(gestor.getId(), null, true, "Uso indevido", new Ator(UUID.randomUUID(), Perfil.ADMIN, null));

            assertThat(gestor.isSuspensaoPermanente()).isTrue();
        }

        @Test
        void usuarioExcluidoNaoEhEncontrado() {
            Usuario ana = registrar(Perfil.CIDADAO);
            ana.anonimizar(AGORA);

            assertThatThrownBy(() -> service.suspender(ana.getId(), null, true, "x",
                    new Ator(UUID.randomUUID(), Perfil.ADMIN, null)))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "USUARIO_NAO_ENCONTRADO");
        }

        @Test
        void reativarQuemNaoEstaSuspenso() {
            Usuario ana = registrar(Perfil.CIDADAO);

            assertThatThrownBy(() -> service.reativar(ana.getId(), new Ator(UUID.randomUUID(), Perfil.MODERADOR, SAO_PAULO)))
                    .hasFieldOrPropertyWithValue("codigo", "USUARIO_NAO_SUSPENSO");
        }

        @Test
        void reativaEAudita() {
            Usuario ana = registrar(Perfil.CIDADAO);
            ana.suspender(null, true, "Spam", AGORA);
            Ator moderador = new Ator(UUID.randomUUID(), Perfil.MODERADOR, SAO_PAULO);

            service.reativar(ana.getId(), moderador);

            assertThat(ana.estaSuspenso(AGORA)).isFalse();
            verify(auditoria).registrar(eq(moderador), eq("USUARIO_REATIVADO"), eq("USUARIO"), eq(ana.getId()), anyMap());
        }
    }

    @Test
    void gestoresSuspensosNaoRecebemOResumoDiario() {
        Usuario ativo = criar(Perfil.GESTOR);
        Usuario suspenso = criar(Perfil.GESTOR);
        suspenso.suspender(null, true, "x", AGORA);
        when(repository.findByPerfilAndExcluidoEmIsNull(Perfil.GESTOR)).thenReturn(List.of(ativo, suspenso));

        assertThat(service.listarGestoresAtivos()).containsExactly(new Gestor(ativo.getId(), SAO_PAULO));
    }

    @Test
    void nomesPublicosDeListaVaziaNaoConsultaOBanco() {
        assertThat(service.nomesPublicos(List.of())).isEmpty();
        verify(repository, never()).findAllById(any());
    }

    private Usuario registrar(Perfil perfil) {
        Usuario u = criar(perfil);
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private static Usuario criar(Perfil perfil) {
        Endereco endereco = new Endereco(new Endereco.Dados("01001000", "Praça da Sé", "Sé", "100", null, SAO_PAULO), AGORA);
        Usuario u = perfil == Perfil.CIDADAO
                ? Usuario.novoCidadao("Ana", "ana@gmail.com", "hash", endereco, AGORA)
                : Usuario.novoMembroDaEquipe("Equipe", "equipe@zelo.app", "hash", perfil, endereco, AGORA);
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        return u;
    }
}
