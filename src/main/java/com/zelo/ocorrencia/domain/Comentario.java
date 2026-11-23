package com.zelo.ocorrencia.domain;

import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
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

/** Comentário em ocorrência. Nunca é anônimo; o autor edita e exclui, a moderação remove. */
@Entity
@Table(name = "comentario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comentario {

    public static final int TEXTO_MAXIMO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID ocorrenciaId;
    private UUID autorId;
    private String texto;
    private Instant criadoEm;
    private Instant editadoEm;
    private Instant removidoEm;
    private String motivoRemocao;

    public Comentario(UUID ocorrenciaId, UUID autorId, String texto, Instant agora) {
        this.ocorrenciaId = ocorrenciaId;
        this.autorId = autorId;
        this.texto = texto.trim();
        this.criadoEm = agora;
    }

    public void editar(UUID usuarioId, String novoTexto, Instant agora) {
        exigirAutor(usuarioId);
        this.texto = novoTexto.trim();
        this.editadoEm = agora;
    }

    public void excluirPeloAutor(UUID usuarioId, Instant agora) {
        exigirAutor(usuarioId);
        this.removidoEm = agora;
        this.motivoRemocao = "Excluído pelo autor";
    }

    public void removerPelaModeracao(String motivo, Instant agora) {
        exigirNaoRemovido();
        if (motivo == null || motivo.isBlank()) {
            throw new RegraDeNegocioException("MOTIVO_OBRIGATORIO", "A remoção exige um motivo.");
        }
        this.removidoEm = agora;
        this.motivoRemocao = motivo.trim();
    }

    public boolean estaRemovido() {
        return removidoEm != null;
    }

    public void exigirNaoRemovido() {
        if (removidoEm != null) {
            throw new RecursoNaoEncontradoException("COMENTARIO_NAO_ENCONTRADO", "Comentário não encontrado.");
        }
    }

    private void exigirAutor(UUID usuarioId) {
        exigirNaoRemovido();
        if (autorId == null || !autorId.equals(usuarioId)) {
            throw new AcessoNegadoException("SOMENTE_AUTOR", "Apenas o autor pode alterar este comentário.");
        }
    }
}
