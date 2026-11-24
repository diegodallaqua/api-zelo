package com.zelo.compartilhado.seguranca;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zelo.compartilhado.erro.LimiteExcedidoException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Rate limit por IP (RNF12), com limite mais restrito nas rotas de autenticação.
 * Em memória: suficiente para uma instância. Com várias instâncias, trocar por Bucket4j distribuído.
 * Atrás de proxy reverso, configure {@code server.forward-headers-strategy} para obter o IP real.
 */
public class FiltroDeRateLimit extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final HandlerExceptionResolver resolver;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public FiltroDeRateLimit(RateLimitProperties properties, HandlerExceptionResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.habilitado() || request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean autenticacao = request.getRequestURI().startsWith("/api/v1/auth/");
        String chave = (autenticacao ? "auth:" : "geral:") + request.getRemoteAddr();
        Bucket bucket = buckets.get(chave, k -> novoBucket(autenticacao));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        long segundos = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));
        resolver.resolveException(request, response, null, new LimiteExcedidoException("LIMITE_DE_REQUISICOES",
                "Muitas requisições em pouco tempo. Tente novamente em " + segundos + " segundo(s)."));
    }

    private Bucket novoBucket(boolean autenticacao) {
        int porMinuto = autenticacao
                ? properties.requisicoesAutenticacaoPorMinuto()
                : properties.requisicoesPorMinuto();
        return Bucket.builder()
                .addLimit(limite -> limite.capacity(porMinuto).refillGreedy(porMinuto, Duration.ofMinutes(1)))
                .build();
    }
}
