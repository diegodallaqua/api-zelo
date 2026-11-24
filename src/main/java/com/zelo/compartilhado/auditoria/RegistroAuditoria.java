package com.zelo.compartilhado.auditoria;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Registro append-only de ação sensível (RNF15). A tabela tem trigger que impede UPDATE/DELETE. */
@Entity
@Immutable
@Table(name = "registro_auditoria")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID atorId;
    private String papelAtor;
    private String acao;
    private String entidade;
    private String entidadeId;
    private String detalhes;
    private Instant ocorridoEm;

    RegistroAuditoria(UUID atorId, String papelAtor, String acao, String entidade, String entidadeId,
                      String detalhes, Instant ocorridoEm) {
        this.atorId = atorId;
        this.papelAtor = papelAtor;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.detalhes = detalhes;
        this.ocorridoEm = ocorridoEm;
    }
}
