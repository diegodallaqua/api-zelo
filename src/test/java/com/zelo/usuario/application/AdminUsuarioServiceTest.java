package com.zelo.usuario.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.infra.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class AdminUsuarioServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");
    private static final int SAO_PAULO = 3550308;
    private static final int RIO = 3304557;
    private static final Endereco.Dados SE = new Endereco.Dados("01001000", "Praça da Sé", "Sé", "100", null, SAO_PAULO);

    @Mock UsuarioRepository usuarios;
    @Mock AutenticacaoService autenticacao;
    @Mock EnderecoService enderecoService;
    @Mock AuditoriaService auditoria;

    private final Ator admin = new Ator(UUID.randomUUID(), Perfil.ADMIN, SAO_PAULO);
    private AdminUsuarioService service;

    @BeforeEach
    void configurar() {
        service = new AdminUsuarioService(usuarios, autenticacao, enderecoService, auditoria,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(autenticacao.hashDeSenha(anyString())).thenReturn("hash");
        lenient().when(enderecoService.novo(any())).thenAnswer(inv -> new Endereco(inv.getArgument(0), AGORA));
        lenient().when(usuarios.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class CriacaoDaEquipe {

        @Test
        void criaGestorSemRestricaoDeDominioEAudita() {
            Usuario gestor = service.criarMembroDaEquipe(" Gestora ", "Gestora@Prefeitura.SP.gov.br", "Senha123",
                    Perfil.GESTOR, SE, admin);

            assertThat(gestor.getNome()).isEqualTo("Gestora");
            assertThat(gestor.getEmail()).isEqualTo("gestora@prefeitura.sp.gov.br");
            assertThat(gestor.getPerfil()).isEqualTo(Perfil.GESTOR);
            assertThat(gestor.cidadeId()).isEqualTo(SAO_PAULO);
            verify(auditoria).registrar(eq(admin), eq("MEMBRO_DA_EQUIPE_CRIADO"), eq("USUARIO"), any(),
                    eq(Map.of("perfil", "GESTOR", "cidadeId", SAO_PAULO)));
        }

        @Test
        void emailRepetido() {
            when(usuarios.existsByEmailIgnoreCase("mod@zelo.app")).thenReturn(true);

            assertThatThrownBy(() -> service.criarMembroDaEquipe("Mod", "MOD@zelo.app", "Senha123", Perfil.MODERADOR,
                    SE, admin))
                    .isInstanceOf(ConflitoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "EMAIL_JA_CADASTRADO");
            verify(usuarios, never()).save(any());
        }

        @Test
        void cidadaoNaoEhCriadoPeloAdministrador() {
            assertThatThrownBy(() -> service.criarMembroDaEquipe("Ana", "ana@gmail.com", "Senha123", Perfil.CIDADAO,
                    SE, admin))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasFieldOrPropertyWithValue("codigo", "PERFIL_INVALIDO");
        }
    }

    @Nested
    class AlteracaoDePerfil {

        @Test
        void alteraEAuditaDeOndeParaOnde() {
            Usuario mod = registrar(Perfil.MODERADOR);

            service.alterarPerfil(mod.getId(), Perfil.GESTOR, admin);

            assertThat(mod.getPerfil()).isEqualTo(Perfil.GESTOR);
            verify(auditoria).registrar(admin, "PERFIL_ALTERADO", "USUARIO", mod.getId(),
                    Map.of("de", "MODERADOR", "para", "GESTOR"));
        }

        @Test
        void naoRebaixaOUnicoAdministrador() {
            Usuario outroAdmin = registrar(Perfil.ADMIN);
            when(usuarios.countByPerfilAndExcluidoEmIsNull(Perfil.ADMIN)).thenReturn(1L);

            assertThatThrownBy(() -> service.alterarPerfil(outroAdmin.getId(), Perfil.MODERADOR, admin))
                    .hasFieldOrPropertyWithValue("codigo", "ULTIMO_ADMINISTRADOR");
            assertThat(outroAdmin.getPerfil()).isEqualTo(Perfil.ADMIN);
        }

        @Test
        void contaExcluidaNaoMuda() {
            Usuario mod = registrar(Perfil.MODERADOR);
            mod.anonimizar(AGORA);

            assertThatThrownBy(() -> service.alterarPerfil(mod.getId(), Perfil.GESTOR, admin))
                    .hasFieldOrPropertyWithValue("codigo", "USUARIO_EXCLUIDO");
        }

        @Test
        void usuarioInexistente() {
            UUID id = UUID.randomUUID();
            when(usuarios.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.alterarPerfil(id, Perfil.GESTOR, admin))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "USUARIO_NAO_ENCONTRADO");
        }
    }

    @Test
    void alterarEnderecoMudaACidadeDeAtuacaoEAudita() {
        Usuario gestor = registrar(Perfil.GESTOR);
        Endereco.Dados rio = new Endereco.Dados("20010000", "Rua 1º de Março", "Centro", "1", null, RIO);

        service.alterarEndereco(gestor.getId(), rio, admin);

        verify(enderecoService).mudar(gestor, rio);
        verify(auditoria).registrar(eq(admin), eq("ENDERECO_ALTERADO"), eq("USUARIO"), eq(gestor.getId()),
                eq(Map.of("cidadeAnterior", String.valueOf(SAO_PAULO), "cidadeNova", RIO)));
    }

    @Test
    void pesquisaIgnoraCuringasDigitadosPeloAdministrador() {
        service.pesquisar(Perfil.GESTOR, SAO_PAULO, "  An%a_ ", false, Pageable.unpaged());
        verify(usuarios).pesquisar(Perfil.GESTOR, SAO_PAULO, "%ana%", false, Pageable.unpaged());

        service.pesquisar(null, null, "   ", true, Pageable.unpaged());
        verify(usuarios).pesquisar(null, null, null, true, Pageable.unpaged());
        verify(auditoria, never()).registrar(any(), anyString(), anyString(), any(), anyMap());
    }

    private Usuario registrar(Perfil perfil) {
        Usuario u = Usuario.novoMembroDaEquipe("Equipe", perfil.name().toLowerCase() + "@zelo.app", "hash", perfil,
                new Endereco(SE, AGORA), AGORA);
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }
}
