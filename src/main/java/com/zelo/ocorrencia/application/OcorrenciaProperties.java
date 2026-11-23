package com.zelo.ocorrencia.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "zelo.ocorrencia")
public record OcorrenciaProperties(ZoneId fusoHorario, int limiteDiarioPorUsuario, double raioDuplicidadeMetros,
                                   Duration prazoContestacao, int diasSemAtividadeParaAviso,
                                   int diasAposAvisoParaArquivar, int anosRetencaoAutor, Mapa mapa, Fotos fotos) {

    public record Mapa(int limitePadrao, int limiteMaximo) {
    }

    public record Fotos(int minimo, int maximo, DataSize tamanhoMaximo, int dimensaoMaxima, float qualidadeJpeg,
                        int megapixelsMaximo) {
    }
}
