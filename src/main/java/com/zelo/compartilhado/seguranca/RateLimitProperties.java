package com.zelo.compartilhado.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zelo.rate-limit")
public record RateLimitProperties(boolean habilitado, int requisicoesPorMinuto, int requisicoesAutenticacaoPorMinuto) {
}
