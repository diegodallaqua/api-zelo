package com.zelo.usuario.application;

import com.zelo.compartilhado.seguranca.SecurityConfig;
import com.zelo.compartilhado.seguranca.SegurancaProperties;
import com.zelo.usuario.domain.RefreshToken;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.infra.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** Emite o JWT de acesso (curto) e o refresh token opaco (longo, revogável). */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final SegurancaProperties properties;
    private final Clock clock;

    public record Tokens(String tokenDeAcesso, String tipo, long expiraEmSegundos, String refreshToken,
                         Instant refreshExpiraEm) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Tokens emitir(Usuario usuario) {
        Instant agora = clock.instant();
        SegurancaProperties.Jwt jwt = properties.jwt();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwt.emissor())
                .issuedAt(agora)
                .expiresAt(agora.plus(jwt.expiracaoAcesso()))
                .subject(usuario.getId().toString())
                .claim(SecurityConfig.CLAIM_PERFIL, usuario.getPerfil().name())
                .build();
        String acesso = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant refreshExpiraEm = agora.plus(jwt.expiracaoRefresh());
        refreshTokens.save(new RefreshToken(usuario.getId(), hash(refresh), refreshExpiraEm, agora));

        return new Tokens(acesso, "Bearer", jwt.expiracaoAcesso().toSeconds(), refresh, refreshExpiraEm);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
