package com.zelo.localidade.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "zelo.localidade")
public record LocalidadeProperties(String avisoCanalNaoOficialPadrao, Ibge ibge) {

    /**
     * @param qualidadeMalha minima, intermediaria ou maxima
     */
    public record Ibge(String urlBase, String qualidadeMalha, boolean importarNaInicializacao, Duration timeout) {
    }
}
