package com.zelo.compartilhado.seguranca;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Autenticação stateless por JWT (HS256) emitido pela própria API. Erros de autenticação e
 * autorização são encaminhados ao {@code ApiExceptionHandler} para manter o mesmo formato de resposta.
 */
@Configuration
public class SecurityConfig {

    public static final String CLAIM_PERFIL = "perfil";

    private static final String[] ROTAS_PUBLICAS_DOCUMENTACAO = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health", "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
                                            CorsConfigurationSource cors) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROTAS_PUBLICAS_DOCUMENTACAO).permitAll()
                        .requestMatchers(HttpMethod.GET, "/arquivos/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole(Perfil.ADMIN.name())
                        .requestMatchers("/api/v1/moderacao/**").hasRole(Perfil.MODERADOR.name())
                        .requestMatchers("/api/v1/gestao/**").hasRole(Perfil.GESTOR.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/ocorrencias/*/transicoes")
                        .hasAnyRole(Perfil.GESTOR.name(), Perfil.MODERADOR.name())
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/ocorrencias", "/api/v1/ocorrencias/**", "/api/v1/categorias/**",
                                "/api/v1/estados/**", "/api/v1/cidades/**", "/api/v1/estatisticas/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDeJwt()))
                        .authenticationEntryPoint((req, res, ex) -> resolver.resolveException(req, res, null, ex))
                        .accessDeniedHandler((req, res, ex) -> resolver.resolveException(req, res, null, ex)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> resolver.resolveException(req, res, null, e))
                        .accessDeniedHandler((req, res, e) -> resolver.resolveException(req, res, null, e)));
        return http.build();
    }

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(Perfil.ADMIN.name()).implies(Perfil.MODERADOR.name(), Perfil.GESTOR.name())
                .role(Perfil.MODERADOR.name()).implies(Perfil.CIDADAO.name())
                .role(Perfil.GESTOR.name()).implies(Perfil.CIDADAO.name())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey chaveJwt(SegurancaProperties properties) {
        String segredo = properties.jwt().segredo();
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Defina ZELO_JWT_SECRET (zelo.seguranca.jwt.segredo) com no mínimo 32 bytes.");
        }
        return new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey chaveJwt, SegurancaProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chaveJwt).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().emissor()));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey chaveJwt) {
        return NimbusJwtEncoder.withSecretKey(chaveJwt).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SegurancaProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().origensPermitidas());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Location", "Retry-After", "X-RateLimit-Remaining"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    FilterRegistrationBean<FiltroDeRateLimit> filtroDeRateLimit(
            RateLimitProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        FilterRegistrationBean<FiltroDeRateLimit> registro =
                new FilterRegistrationBean<>(new FiltroDeRateLimit(properties, resolver));
        // Antes da cadeia do Spring Security (ordem -100), para barrar abuso antes de autenticar.
        registro.setOrder(-150);
        return registro;
    }

    private static Converter<Jwt, AbstractAuthenticationToken> conversorDeJwt() {
        return jwt -> {
            try {
                UUID id = UUID.fromString(jwt.getSubject());
                Perfil perfil = Perfil.valueOf(jwt.getClaimAsString(CLAIM_PERFIL));
                return new ZeloAuthenticationToken(new UsuarioAutenticado(id, perfil), jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name())));
            } catch (RuntimeException e) {
                throw new InvalidBearerTokenException("Token com conteúdo inválido");
            }
        };
    }
}
