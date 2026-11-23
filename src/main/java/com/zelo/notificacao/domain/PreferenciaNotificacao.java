package com.zelo.notificacao.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Canais escolhidos pelo usuário (regra 3.6). Sem registro, vale o padrão: push ligado, e-mail desligado. */
@Entity
@Table(name = "preferencia_notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreferenciaNotificacao {

    @Id
    private UUID usuarioId;

    private boolean push;
    private boolean email;
    private Instant atualizadaEm;

    public PreferenciaNotificacao(UUID usuarioId, boolean push, boolean email, Instant agora) {
        this.usuarioId = usuarioId;
        this.push = push;
        this.email = email;
        this.atualizadaEm = agora;
    }

    public static PreferenciaNotificacao padrao(UUID usuarioId, Instant agora) {
        return new PreferenciaNotificacao(usuarioId, true, false, agora);
    }

    public void alterar(boolean push, boolean email, Instant agora) {
        this.push = push;
        this.email = email;
        this.atualizadaEm = agora;
    }

    public Set<Notificador.Canal> canais() {
        Set<Notificador.Canal> canais = EnumSet.noneOf(Notificador.Canal.class);
        if (push) {
            canais.add(Notificador.Canal.PUSH);
        }
        if (email) {
            canais.add(Notificador.Canal.EMAIL);
        }
        return canais;
    }
}
