package com.zelo.usuario.application;

import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.NaoAutenticadoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.erro.RequisicaoInvalidaException;
import com.zelo.usuario.application.TokenService.Tokens;
import com.zelo.usuario.domain.Consentimento;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.RefreshToken;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.domain.ValidadorDeDominioDeEmail;
import com.zelo.usuario.infra.ConsentimentoRepository;
import com.zelo.usuario.infra.RefreshTokenRepository;
import com.zelo.usuario.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AutenticacaoService {

    /** Usado quando o e-mail não existe, para o tempo de resposta não revelar contas cadastradas. */
    private static final String HASH_FICTICIO = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BWX4Z8hW0Eqh0F/Txnk5QzvxNnG6";

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final ConsentimentoRepository consentimentos;
    private final ValidadorDeDominioDeEmail validadorDeDominio;
    private final EnderecoService enderecoService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UsuarioProperties properties;
    private final Clock clock;

    @Transactional
    public Tokens cadastrar(String nome, String email, String senha, Endereco.Dados endereco, boolean aceitouTermos,
                            boolean aceitouPolitica) {
        String emailNormalizado = normalizarEmail(email);
        validadorDeDominio.validar(emailNormalizado);
        if (!aceitouTermos || !aceitouPolitica) {
            throw new RegraDeNegocioException("CONSENTIMENTO_OBRIGATORIO",
                    "É necessário aceitar os Termos de Uso e a Política de Privacidade para criar a conta.");
        }
        if (usuarios.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new ConflitoException("EMAIL_JA_CADASTRADO", "Já existe uma conta com este e-mail.");
        }
        Instant agora = clock.instant();
        Usuario usuario = usuarios.save(Usuario.novoCidadao(nome.trim(), emailNormalizado, hashDeSenha(senha),
                enderecoService.novo(endereco), agora));
        UsuarioProperties.Cadastro cadastro = properties.cadastro();
        consentimentos.save(new Consentimento(usuario.getId(), Consentimento.Tipo.TERMOS_DE_USO,
                cadastro.versaoTermosDeUso(), true, agora));
        consentimentos.save(new Consentimento(usuario.getId(), Consentimento.Tipo.POLITICA_DE_PRIVACIDADE,
                cadastro.versaoPoliticaDePrivacidade(), true, agora));
        return tokenService.emitir(usuario);
    }

    @Transactional
    public Tokens login(String email, String senha) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(normalizarEmail(email))
                .filter(u -> !u.estaExcluido() && u.getSenhaHash() != null)
                .orElse(null);
        String hash = usuario != null ? usuario.getSenhaHash() : HASH_FICTICIO;
        boolean senhaConfere = passwordEncoder.matches(senha, hash);
        if (usuario == null || !senhaConfere) {
            throw new NaoAutenticadoException("CREDENCIAIS_INVALIDAS", "E-mail ou senha inválidos.");
        }
        return tokenService.emitir(usuario);
    }

    /**
     * Troca o refresh token por um novo par (rotação). A reutilização de um token já revogado indica
     * vazamento: todas as sessões do usuário são encerradas.
     */
    @Transactional(noRollbackFor = NaoAutenticadoException.class)
    public Tokens renovar(String refreshToken) {
        Instant agora = clock.instant();
        RefreshToken atual = refreshTokens.findByTokenHash(TokenService.hash(refreshToken))
                .orElseThrow(AutenticacaoService::refreshInvalido);
        if (atual.revogado()) {
            refreshTokens.revogarTodos(atual.getUsuarioId(), agora);
            throw new NaoAutenticadoException("REFRESH_TOKEN_REUTILIZADO",
                    "Este token de renovação já foi usado. Por segurança, todas as sessões foram encerradas.");
        }
        if (atual.expirado(agora)) {
            throw new NaoAutenticadoException("REFRESH_TOKEN_EXPIRADO", "Sessão expirada. Faça login novamente.");
        }
        Usuario usuario = usuarios.findById(atual.getUsuarioId())
                .filter(u -> !u.estaExcluido())
                .orElseThrow(AutenticacaoService::refreshInvalido);
        atual.revogar(agora);
        return tokenService.emitir(usuario);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(TokenService.hash(refreshToken)).ifPresent(t -> t.revogar(clock.instant()));
    }

    /** bcrypt considera só os primeiros 72 bytes; senhas maiores são recusadas. */
    String hashDeSenha(String senha) {
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new RequisicaoInvalidaException("SENHA_INVALIDA", "A senha deve ter no máximo 72 bytes.");
        }
        return passwordEncoder.encode(senha);
    }

    static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static NaoAutenticadoException refreshInvalido() {
        return new NaoAutenticadoException("REFRESH_TOKEN_INVALIDO", "Token de renovação inválido. Faça login novamente.");
    }
}
