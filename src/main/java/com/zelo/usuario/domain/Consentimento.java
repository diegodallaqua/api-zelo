package com.zelo.usuario.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Registro de consentimento (LGPD), com a versão do documento aceito. Nunca é alterado. */
@Entity
@Table(name = "consentimento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consentimento {

    public enum Tipo { TERMOS_DE_USO, POLITICA_DE_PRIVACIDADE, LOCALIZACAO }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    private Tipo tipo;

    private String versao;
    private boolean aceito;
    private Instant registradoEm;

    public Consentimento(UUID usuarioId, Tipo tipo, String versao, boolean aceito, Instant agora) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.versao = versao;
        this.aceito = aceito;
        this.registradoEm = agora;
    }
}
