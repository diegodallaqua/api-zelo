package com.zelo.categoria.domain;

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

/** Categoria de ocorrência, válida em todas as cidades e configurável pelo administrador. */
@Entity
@Table(name = "categoria")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nome;
    private String descricao;
    private String icone;
    private boolean ativa;
    private Instant criadaEm;
    private Instant atualizadaEm;

    public Categoria(String nome, String descricao, String icone, Instant agora) {
        this.nome = nome;
        this.descricao = descricao;
        this.icone = icone;
        this.ativa = true;
        this.criadaEm = agora;
        this.atualizadaEm = agora;
    }

    public void atualizar(String nome, String descricao, String icone, boolean ativa, Instant agora) {
        this.nome = nome;
        this.descricao = descricao;
        this.icone = icone;
        this.ativa = ativa;
        this.atualizadaEm = agora;
    }
}
