package com.zelo.notificacao.domain;

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

/** Notificação exibida dentro do app. Push e e-mail saem pelo {@link Notificador}. */
@Entity
@Table(name = "notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notificacao {

    public enum Tipo {
        STATUS_ALTERADO, AVISO_ARQUIVAMENTO, OCORRENCIA_MESCLADA, CONTEUDO_REMOVIDO, CONTA_SUSPENSA, RESUMO_GESTOR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    private Tipo tipo;

    private String titulo;
    private String mensagem;
    private UUID ocorrenciaId;
    private Instant lidaEm;
    private Instant criadaEm;

    public Notificacao(UUID usuarioId, Tipo tipo, String titulo, String mensagem, UUID ocorrenciaId, Instant agora) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensagem = mensagem.length() > 1000 ? mensagem.substring(0, 997) + "..." : mensagem;
        this.ocorrenciaId = ocorrenciaId;
        this.criadaEm = agora;
    }

    public void marcarComoLida(Instant agora) {
        if (lidaEm == null) {
            lidaEm = agora;
        }
    }
}
