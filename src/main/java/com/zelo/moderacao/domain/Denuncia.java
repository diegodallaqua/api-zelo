package com.zelo.moderacao.domain;

import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.ConflitoException;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Denúncia de exatamente uma ocorrência ou um comentário (RF18). Uma por usuário por conteúdo. */
@Entity
@Table(name = "denuncia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Denuncia {

    public enum Motivo { SPAM, OFENSA, INFORMACAO_FALSA, LOCAL_ERRADO, OUTRO }

    public enum Status { PENDENTE, PROCEDENTE, IMPROCEDENTE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID denuncianteId;

    @Convert(converter = TipoConteudo.ConversorJpa.class)
    @Column(name = "tipo_conteudo_id")
    private TipoConteudo tipoConteudo;

    private UUID ocorrenciaId;
    private UUID comentarioId;
    private Integer cidadeId;

    @Enumerated(EnumType.STRING)
    private Motivo motivo;

    private String descricao;

    @Enumerated(EnumType.STRING)
    private Status status;

    private UUID decisaoId;
    private Instant criadaEm;
    private Instant atualizadaEm;
    private Instant resolvidaEm;

    public Denuncia(UUID denuncianteId, Alvo alvo, Integer cidadeId, Motivo motivo, String descricao, Instant agora) {
        this.denuncianteId = denuncianteId;
        aplicar(alvo, cidadeId, motivo, descricao);
        this.status = Status.PENDENTE;
        this.criadaEm = agora;
        this.atualizadaEm = agora;
    }

    /** O denunciante corrige a própria denúncia enquanto ela ainda não foi analisada. */
    public void alterar(UUID usuarioId, Alvo alvo, Integer cidadeId, Motivo motivo, String descricao, Instant agora) {
        exigirPodeAlterar(usuarioId);
        aplicar(alvo, cidadeId, motivo, descricao);
        this.atualizadaEm = agora;
    }

    /** Só o denunciante altera, e só enquanto a denúncia está pendente. */
    public void exigirPodeAlterar(UUID usuarioId) {
        if (!denuncianteId.equals(usuarioId)) {
            throw new AcessoNegadoException("SOMENTE_DENUNCIANTE", "Apenas quem fez a denúncia pode alterá-la.");
        }
        if (status != Status.PENDENTE) {
            throw new ConflitoException("DENUNCIA_JA_ANALISADA", "A denúncia já foi analisada pela moderação.");
        }
    }

    public void resolver(Status resultado, UUID decisaoId, Instant agora) {
        this.status = resultado;
        this.decisaoId = decisaoId;
        this.resolvidaEm = agora;
    }

    public Alvo alvo() {
        return new Alvo(tipoConteudo, ocorrenciaId, comentarioId);
    }

    private void aplicar(Alvo alvo, Integer cidadeId, Motivo motivo, String descricao) {
        this.tipoConteudo = alvo.tipo();
        this.ocorrenciaId = alvo.ocorrenciaId();
        this.comentarioId = alvo.comentarioId();
        this.cidadeId = cidadeId;
        this.motivo = motivo;
        this.descricao = descricao;
    }
}
