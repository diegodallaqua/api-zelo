package com.zelo.usuario.application;

import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.NaoAutenticadoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.erro.RequisicaoInvalidaException;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.usuario.application.TokenService.Tokens;
import com.zelo.usuario.domain.Consentimento;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.RefreshToken;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.domain.ValidadorDeDominioDeEmail;
import com.zelo.usuario.infra.ConsentimentoRepository;
import com.zelo.usuario.infra.RefreshTokenRepository;
import com.zelo.usuario.infra.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");
    private static final Endereco.Dados ENDERECO =
            new Endereco.Dados("01001-000", "Praça da Sé", "Sé", "100", null, 3550308);

    @Mock UsuarioRepository usuarios;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock ConsentimentoRepository consentimentos;
    @Mock EnderecoService enderecoService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenService tokenService;

    private final Tokens tokens = new Tokens("acesso", "Bearer", 900, "refresh", AGORA.plusSeconds(3600));
    private AutenticacaoService service;

    @BeforeEach
    void configurar() {
        UsuarioProperties properties = new UsuarioProperties(
                new UsuarioProperties.Cadastro(List.of("gmail.com"), "1.0", "2.0", "3.0"), null);
        service = new AutenticacaoService(usuarios, refreshTokens, consentimentos,
                new ValidadorDeDominioDeEmail(List.of("gmail.com")), enderecoService, passwordEncoder, tokenService,
                properties, Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        lenient().when(tokenService.emitir(any())).thenReturn(tokens);
        lenient().when(usuarios.save(any())).thenAnswer(inv -> comId(inv.getArgument(0)));
        lenient().when(enderecoService.novo(any())).thenAnswer(inv -> new Endereco(inv.getArgument(0), AGORA));
    }

    @Nested
    class Cadastro {

        @Test
        void criaCidadaoComEmailNormalizadoConsentimentosETokens() {
            Tokens emitidos = service.cadastrar("  Ana  ", "  Ana@GMAIL.com ", "Senha123", ENDERECO, true, true);

            assertThat(emitidos).isSameAs(tokens);
            ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarios).save(usuario.capture());
            assertThat(usuario.getValue().getNome()).isEqualTo("Ana");
            assertThat(usuario.getValue().getEmail()).isEqualTo("ana@gmail.com");
            assertThat(usuario.getValue().getPerfil()).isEqualTo(Perfil.CIDADAO);
            assertThat(usuario.getValue().getSenhaHash()).isEqualTo("hash:Senha123");
            assertThat(usuario.getValue().cidadeId()).isEqualTo(3550308);

            ArgumentCaptor<Consentimento> consentimento = ArgumentCaptor.forClass(Consentimento.class);
            verify(consentimentos, times(2)).save(consentimento.capture());
            assertThat(consentimento.getAllValues())
                    .extracting(Consentimento::getTipo, Consentimento::getVersao, Consentimento::isAceito)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(Consentimento.Tipo.TERMOS_DE_USO, "1.0", true),
                            org.assertj.core.groups.Tuple.tuple(Consentimento.Tipo.POLITICA_DE_PRIVACIDADE, "2.0", true));
        }

        @Test
        void recusaDominioNaoPermitido() {
            assertThatThrownBy(() -> service.cadastrar("Ana", "ana@empresa.com.br", "Senha123", ENDERECO, true, true))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasFieldOrPropertyWithValue("codigo", "DOMINIO_DE_EMAIL_NAO_PERMITIDO");
            verify(usuarios, never()).save(any());
        }

        @Test
        void exigeAceiteDosTermosEDaPolitica() {
            assertThatThrownBy(() -> service.cadastrar("Ana", "ana@gmail.com", "Senha123", ENDERECO, true, false))
                    .hasFieldOrPropertyWithValue("codigo", "CONSENTIMENTO_OBRIGATORIO");
            assertThatThrownBy(() -> service.cadastrar("Ana", "ana@gmail.com", "Senha123", ENDERECO, false, true))
                    .hasFieldOrPropertyWithValue("codigo", "CONSENTIMENTO_OBRIGATORIO");
            verify(usuarios, never()).save(any());
        }

        @Test
        void emailJaCadastradoEhConflito() {
            when(usuarios.existsByEmailIgnoreCase("ana@gmail.com")).thenReturn(true);

            assertThatThrownBy(() -> service.cadastrar("Ana", "ANA@gmail.com", "Senha123", ENDERECO, true, true))
                    .isInstanceOf(ConflitoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "EMAIL_JA_CADASTRADO");
        }

        @Test
        void senhaAcimaDe72BytesEhRecusada() {
            // 37 caracteres de 2 bytes = 74 bytes em UTF-8, apesar de ter menos de 72 caracteres.
            String senha = "ç".repeat(37);

            assertThatThrownBy(() -> service.cadastrar("Ana", "ana@gmail.com", senha, ENDERECO, true, true))
                    .isInstanceOf(RequisicaoInvalidaException.class)
                    .hasFieldOrPropertyWithValue("codigo", "SENHA_INVALIDA");
        }
    }

    @Nested
    class Login {

        @Test
        void emiteTokensComSenhaCorreta() {
            Usuario ana = cidadao("hash-da-ana");
            when(usuarios.findByEmailIgnoreCase("ana@gmail.com")).thenReturn(Optional.of(ana));
            when(passwordEncoder.matches("Senha123", "hash-da-ana")).thenReturn(true);

            assertThat(service.login(" ANA@gmail.com", "Senha123")).isSameAs(tokens);
            verify(tokenService).emitir(ana);
        }

        @Test
        void senhaErradaNaoAutentica() {
            when(usuarios.findByEmailIgnoreCase("ana@gmail.com")).thenReturn(Optional.of(cidadao("hash-da-ana")));
            when(passwordEncoder.matches("errada", "hash-da-ana")).thenReturn(false);

            assertThatThrownBy(() -> service.login("ana@gmail.com", "errada"))
                    .isInstanceOf(NaoAutenticadoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "CREDENCIAIS_INVALIDAS");
            verify(tokenService, never()).emitir(any());
        }

        @Test
        void emailInexistenteAindaComparaUmHashParaNaoRevelarContas() {
            when(usuarios.findByEmailIgnoreCase("ninguem@gmail.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.login("ninguem@gmail.com", "Senha123"))
                    .hasFieldOrPropertyWithValue("codigo", "CREDENCIAIS_INVALIDAS");
            verify(passwordEncoder).matches(eq("Senha123"), anyString());
        }

        @Test
        void contaExcluidaNaoFazLogin() {
            Usuario excluida = cidadao("hash");
            excluida.anonimizar(AGORA);
            when(usuarios.findByEmailIgnoreCase("ana@gmail.com")).thenReturn(Optional.of(excluida));

            assertThatThrownBy(() -> service.login("ana@gmail.com", "Senha123"))
                    .hasFieldOrPropertyWithValue("codigo", "CREDENCIAIS_INVALIDAS");
        }
    }

    @Nested
    class Renovacao {

        private final Usuario ana = cidadao("hash");

        @Test
        void rotacionaORefreshToken() {
            RefreshToken atual = refresh(AGORA.plusSeconds(60));
            when(refreshTokens.findByTokenHash(TokenService.hash("token"))).thenReturn(Optional.of(atual));
            when(usuarios.findById(ana.getId())).thenReturn(Optional.of(ana));

            assertThat(service.renovar("token")).isSameAs(tokens);
            assertThat(atual.revogado()).isTrue();
        }

        @Test
        void tokenDesconhecido() {
            when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.renovar("qualquer"))
                    .isInstanceOf(NaoAutenticadoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "REFRESH_TOKEN_INVALIDO");
        }

        @Test
        void reutilizacaoEncerraTodasAsSessoes() {
            RefreshToken usado = refresh(AGORA.plusSeconds(60));
            usado.revogar(AGORA.minusSeconds(10));
            when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.of(usado));

            assertThatThrownBy(() -> service.renovar("token"))
                    .hasFieldOrPropertyWithValue("codigo", "REFRESH_TOKEN_REUTILIZADO");
            verify(refreshTokens).revogarTodos(ana.getId(), AGORA);
            verify(tokenService, never()).emitir(any());
        }

        @Test
        void tokenExpirado() {
            when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.of(refresh(AGORA)));

            assertThatThrownBy(() -> service.renovar("token"))
                    .hasFieldOrPropertyWithValue("codigo", "REFRESH_TOKEN_EXPIRADO");
        }

        @Test
        void contaExcluidaNaoRenova() {
            ana.anonimizar(AGORA);
            when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.of(refresh(AGORA.plusSeconds(60))));
            when(usuarios.findById(ana.getId())).thenReturn(Optional.of(ana));

            assertThatThrownBy(() -> service.renovar("token"))
                    .hasFieldOrPropertyWithValue("codigo", "REFRESH_TOKEN_INVALIDO");
        }

        @Test
        void logoutRevogaOToken() {
            RefreshToken atual = refresh(AGORA.plusSeconds(60));
            when(refreshTokens.findByTokenHash(TokenService.hash("token"))).thenReturn(Optional.of(atual));

            service.logout("token");

            assertThat(atual.getRevogadoEm()).isEqualTo(AGORA);
        }

        private RefreshToken refresh(Instant expiraEm) {
            return new RefreshToken(ana.getId(), TokenService.hash("token"), expiraEm, AGORA.minusSeconds(3600));
        }
    }

    private static Usuario cidadao(String hash) {
        return comId(Usuario.novoCidadao("Ana", "ana@gmail.com", hash, new Endereco(ENDERECO, AGORA), AGORA));
    }

    private static Usuario comId(Usuario usuario) {
        if (usuario.getId() == null) {
            ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        }
        return usuario;
    }
}
