package com.zelo.usuario.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Refresh token opaco. Só o hash SHA-256 é persistido; cada uso gera um novo (rotação). */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID usuarioId;
    private String tokenHash;
    private Instant expiraEm;
    private Instant revogadoEm;
    private Instant criadoEm;

    public RefreshToken(UUID usuarioId, String tokenHash, Instant expiraEm, Instant agora) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiraEm = expiraEm;
        this.criadoEm = agora;
    }

    public boolean revogado() {
        return revogadoEm != null;
    }

    public boolean expirado(Instant agora) {
        return !expiraEm.isAfter(agora);
    }

    public void revogar(Instant agora) {
        if (revogadoEm == null) {
            revogadoEm = agora;
        }
    }
}
