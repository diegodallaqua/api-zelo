package com.zelo.compartilhado.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "zelo.seguranca")
public record SegurancaProperties(Jwt jwt, Cors cors) {

    public record Jwt(String segredo, String emissor, Duration expiracaoAcesso, Duration expiracaoRefresh) {
    }

    public record Cors(List<String> origensPermitidas) {
    }
}
