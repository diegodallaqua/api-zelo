package com.zelo.ocorrencia.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Foto já processada (redimensionada, sem EXIF) e gravada no armazenamento sob {@code chave}. */
@Entity
@Table(name = "foto_ocorrencia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FotoOcorrencia {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocorrencia_id")
    private Ocorrencia ocorrencia;

    private String chave;
    private String contentType;
    private int tamanhoBytes;
    private int largura;
    private int altura;
    private int ordem;
    private Instant criadaEm;

    FotoOcorrencia(UUID id, Ocorrencia ocorrencia, String chave, String contentType, int tamanhoBytes, int largura,
                   int altura, int ordem, Instant criadaEm) {
        this.id = id;
        this.ocorrencia = ocorrencia;
        this.chave = chave;
        this.contentType = contentType;
        this.tamanhoBytes = tamanhoBytes;
        this.largura = largura;
        this.altura = altura;
        this.ordem = ordem;
        this.criadaEm = criadaEm;
    }
}
