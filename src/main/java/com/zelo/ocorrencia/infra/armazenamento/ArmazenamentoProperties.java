package com.zelo.ocorrencia.infra.armazenamento;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zelo.armazenamento")
public record ArmazenamentoProperties(String tipo, Local local, R2 r2) {

    public record Local(String diretorio, String urlBase) {
    }

    /** @param urlPublica domínio público do bucket (r2.dev ou domínio próprio), sem barra no final */
    public record R2(String accountId, String accessKeyId, String secretAccessKey, String bucket, String urlPublica) {
    }
}
