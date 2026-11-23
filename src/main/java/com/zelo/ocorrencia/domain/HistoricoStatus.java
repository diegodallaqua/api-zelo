package com.zelo.ocorrencia.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Entrada imutável do histórico de uma ocorrência (RF15): quem, quando, de/para e motivo. */
@Entity
@Immutable
@Table(name = "historico_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HistoricoStatus {

    public enum TipoEvento { CRIACAO, TRANSICAO, CONTESTACAO, CONFIRMACAO }

    /** Papel de quem agiu. O id só é gravado para a equipe (ver migração V5). */
    public enum Papel { AUTOR, MODERADOR, GESTOR, ADMIN, SISTEMA }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocorrencia_id")
    private Ocorrencia ocorrencia;

    @Enumerated(EnumType.STRING)
    private TipoEvento tipoEvento;

    @Enumerated(EnumType.STRING)
    private StatusOcorrencia statusAnterior;

    @Enumerated(EnumType.STRING)
    private StatusOcorrencia statusNovo;

    private UUID autorId;

    @Enumerated(EnumType.STRING)
    private Papel papelAutor;

    private String motivo;
    private Instant ocorridoEm;

    HistoricoStatus(Ocorrencia ocorrencia, TipoEvento tipoEvento, StatusOcorrencia statusAnterior,
                    StatusOcorrencia statusNovo, UUID autorId, Papel papelAutor, String motivo, Instant ocorridoEm) {
        this.id = UUID.randomUUID();
        this.ocorrencia = ocorrencia;
        this.tipoEvento = tipoEvento;
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.autorId = papelAutor == Papel.AUTOR ? null : autorId;
        this.papelAutor = papelAutor;
        this.motivo = motivo;
        this.ocorridoEm = ocorridoEm;
    }
}
