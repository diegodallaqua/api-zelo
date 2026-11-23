package com.zelo.moderacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Decisão do moderador, sempre com motivo (fluxo 2.3). Imutável. Decisões sobre conteúdo apontam para
 * exatamente uma ocorrência ou um comentário; suspensão e reativação diretas de usuário não têm conteúdo.
 */
@Entity
@Immutable
@Table(name = "decisao_moderacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DecisaoModeracao {

    public enum Acao {
        /** Denúncias improcedentes; o conteúdo fica. */
        MANTER,
        REMOVER_CONTEUDO,
        MESCLAR_DUPLICATA,
        /** Suspende o autor do conteúdo denunciado (opcionalmente removendo o conteúdo). */
        SUSPENDER_AUTOR,
        /** Suspensão direta, fora de uma denúncia. Sem conteúdo. */
        SUSPENDER_USUARIO,
        /** Fim de suspensão. Sem conteúdo. */
        REATIVAR_USUARIO;

        public boolean sobreConteudo() {
            return this != SUSPENDER_USUARIO && this != REATIVAR_USUARIO;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID moderadorId;

    @Enumerated(EnumType.STRING)
    private Acao acao;

    @Convert(converter = TipoConteudo.ConversorJpa.class)
    @Column(name = "tipo_conteudo_id")
    private TipoConteudo tipoConteudo;

    private UUID ocorrenciaId;
    private UUID comentarioId;
    private UUID usuarioAfetadoId;
    private UUID ocorrenciaPrincipalId;
    private Instant suspensaoAte;
    private boolean suspensaoPermanente;
    private String motivo;
    private Instant decididaEm;

    @Builder
    private DecisaoModeracao(UUID moderadorId, Acao acao, Alvo alvo, UUID usuarioAfetadoId,
                             UUID ocorrenciaPrincipalId, Instant suspensaoAte, boolean suspensaoPermanente,
                             String motivo, Instant decididaEm) {
        if (acao.sobreConteudo() == (alvo == null)) {
            throw new IllegalArgumentException("Decisão " + acao + (alvo == null ? " exige" : " não aceita") + " conteúdo alvo.");
        }
        this.moderadorId = moderadorId;
        this.acao = acao;
        if (alvo != null) {
            this.tipoConteudo = alvo.tipo();
            this.ocorrenciaId = alvo.ocorrenciaId();
            this.comentarioId = alvo.comentarioId();
        }
        this.usuarioAfetadoId = usuarioAfetadoId;
        this.ocorrenciaPrincipalId = ocorrenciaPrincipalId;
        this.suspensaoAte = suspensaoAte;
        this.suspensaoPermanente = suspensaoPermanente;
        this.motivo = motivo;
        this.decididaEm = decididaEm;
    }
}
