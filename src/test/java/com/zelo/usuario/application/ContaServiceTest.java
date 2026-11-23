package com.zelo.usuario.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.NaoAutenticadoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.usuario.domain.Consentimento;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.EventosDeUsuario.ContaExcluida;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.infra.ConsentimentoRepository;
import com.zelo.usuario.infra.RefreshTokenRepository;
import com.zelo.usuario.infra.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");
    private static final Endereco.Dados SE = new Endereco.Dados("01001000", "Praça da Sé", "Sé", "100", null, 3550308);
    private static final Endereco.Dados RIO = new Endereco.Dados("20010000", "Rua 1º de Março", "Centro", "1", null, 3304557);

    @Mock UsuarioRepository usuarios;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock ConsentimentoRepository consentimentos;
    @Mock AutenticacaoService autenticacao;
    @Mock EnderecoService enderecoService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditoriaService auditoria;
    @Mock ApplicationEventPublisher eventos;

    private ContaService service;

    @BeforeEach
    void configurar() {
        UsuarioProperties properties = new UsuarioProperties(
                new UsuarioProperties.Cadastro(List.of("gmail.com"), "1.0", "2.0", "3.0"), null);
        service = new ContaService(usuarios, refreshTokens, consentimentos, autenticacao, enderecoService,
                passwordEncoder, properties, auditoria, eventos, Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(passwordEncoder.matches("Senha123", "hash")).thenReturn(true);
    }

    @Test
    void contaExcluidaFicaIndisponivel() {
        Usuario ana = registrar(cidadao());
        ana.anonimizar(AGORA);

        assertThatThrownBy(() -> service.obter(ana.getId()))
                .isInstanceOf(NaoAutenticadoException.class)
                .hasFieldOrPropertyWithValue("codigo", "CONTA_INDISPONIVEL");
    }

    @Test
    void alteraONomeSemEspacosNasPontas() {
        Usuario ana = registrar(cidadao());

        assertThat(service.alterarNome(ana.getId(), "  Ana Maria  ").getNome()).isEqualTo("Ana Maria");
    }

    @Nested
    class AlteracaoDeEndereco {

        @Test
        void cidadaoAlteraOProprioEndereco() {
            Usuario ana = registrar(cidadao());

            service.alterarEndereco(ana.getId(), RIO);

            verify(enderecoService).mudar(ana, RIO);
        }

        @ParameterizedTest
        @EnumSource(value = Perfil.class, names = {"MODERADOR", "GESTOR"})
        void equipeQueAtuaPorCidadeNaoAlteraOProprioEndereco(Perfil perfil) {
            Usuario membro = registrar(Usuario.novoMembroDaEquipe("Equipe", "e@zelo.app", "hash", perfil,
                    new Endereco(SE, AGORA), AGORA));

            assertThatThrownBy(() -> service.alterarEndereco(membro.getId(), RIO))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "ENDERECO_DEFINE_ATUACAO");
            verify(enderecoService, never()).mudar(any(), any());
        }
    }

    @Nested
    class Senha {

        @Test
        void trocaASenhaEEncerraAsSessoes() {
            Usuario ana = registrar(cidadao());
            when(autenticacao.hashDeSenha("NovaSenha1")).thenReturn("novo-hash");

            service.alterarSenha(ana.getId(), "Senha123", "NovaSenha1");

            assertThat(ana.getSenhaHash()).isEqualTo("novo-hash");
            verify(refreshTokens).revogarTodos(ana.getId(), AGORA);
        }

        @Test
        void senhaAtualIncorreta() {
            Usuario ana = registrar(cidadao());

            assertThatThrownBy(() -> service.alterarSenha(ana.getId(), "errada", "NovaSenha1"))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasFieldOrPropertyWithValue("codigo", "SENHA_INCORRETA");
            verify(refreshTokens, never()).revogarTodos(any(), any());
        }
    }

    @Nested
    class Exclusao {

        @Test
        void anonimizaAvisaOsModulosEApagaOEnderecoOrfao() {
            Usuario ana = registrar(cidadao());
            UUID enderecoId = UUID.randomUUID();
            ReflectionTestUtils.setField(ana.getEndereco(), "id", enderecoId);

            service.excluirConta(ana.getId(), "Senha123");

            InOrder ordem = inOrder(eventos, refreshTokens, usuarios, enderecoService);
            ordem.verify(eventos).publishEvent(new ContaExcluida(ana.getId()));
            ordem.verify(refreshTokens).excluirTodos(ana.getId());
            ordem.verify(usuarios).flush();
            ordem.verify(enderecoService).removerSeOrfao(enderecoId);
            assertThat(ana.estaExcluido()).isTrue();
            assertThat(ana.getNome()).isEqualTo(Usuario.NOME_ANONIMIZADO);
            assertThat(ana.getEndereco()).isNull();
            verify(auditoria).registrarPeloSistema(eq("CONTA_EXCLUIDA_PELO_TITULAR"), eq("USUARIO"), eq(ana.getId()), anyMap());
        }

        @Test
        void exigeASenha() {
            Usuario ana = registrar(cidadao());

            assertThatThrownBy(() -> service.excluirConta(ana.getId(), "errada"))
                    .hasFieldOrPropertyWithValue("codigo", "SENHA_INCORRETA");
            assertThat(ana.estaExcluido()).isFalse();
        }

        @Test
        void naoExcluiOUnicoAdministrador() {
            Usuario admin = registrar(Usuario.novoMembroDaEquipe("Admin", "admin@zelo.app", "hash", Perfil.ADMIN,
                    new Endereco(SE, AGORA), AGORA));
            when(usuarios.countByPerfilAndExcluidoEmIsNull(Perfil.ADMIN)).thenReturn(1L);

            assertThatThrownBy(() -> service.excluirConta(admin.getId(), "Senha123"))
                    .hasFieldOrPropertyWithValue("codigo", "ULTIMO_ADMINISTRADOR");
            verify(eventos, never()).publishEvent(any(Object.class));
        }

        @Test
        void administradorPodeSairSeHouverOutro() {
            Usuario admin = registrar(Usuario.novoMembroDaEquipe("Admin", "admin@zelo.app", "hash", Perfil.ADMIN,
                    new Endereco(SE, AGORA), AGORA));
            when(usuarios.countByPerfilAndExcluidoEmIsNull(Perfil.ADMIN)).thenReturn(2L);

            service.excluirConta(admin.getId(), "Senha123");

            assertThat(admin.estaExcluido()).isTrue();
            assertThat(admin.getPerfil()).isEqualTo(Perfil.CIDADAO);
        }
    }

    @Test
    void consentimentoDeLocalizacaoUsaAVersaoVigente() {
        Usuario ana = registrar(cidadao());
        when(consentimentos.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Consentimento c = service.registrarConsentimento(ana.getId(), Consentimento.Tipo.LOCALIZACAO, false);

        assertThat(c.getVersao()).isEqualTo("3.0");
        assertThat(c.isAceito()).isFalse();
        assertThat(c.getRegistradoEm()).isEqualTo(AGORA);
    }

    private static Usuario cidadao() {
        return Usuario.novoCidadao("Ana", "ana@gmail.com", "hash", new Endereco(SE, AGORA), AGORA);
    }

    private Usuario registrar(Usuario usuario) {
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        when(usuarios.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        return usuario;
    }
}
