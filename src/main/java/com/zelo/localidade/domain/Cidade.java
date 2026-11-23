package com.zelo.localidade.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Cidade brasileira. O id é o código IBGE. O polígono ({@code limite}) não é mapeado: só é usado em
 * consultas espaciais nativas e gravado pela importação do IBGE.
 */
@Entity
@Table(name = "cidade")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cidade {

    @Id
    private Integer id;

    private String nome;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "estado_id")
    private Estado estado;

    private boolean ativo;
    private String avisoCanalNaoOficial;
    private String urlCanalOficial;
    private String textoLegal;
    private Instant criadoEm;
    private Instant atualizadoEm;

    public void configurar(boolean ativo, String avisoCanalNaoOficial, String urlCanalOficial, String textoLegal,
                           Instant agora) {
        this.ativo = ativo;
        this.avisoCanalNaoOficial = avisoCanalNaoOficial;
        this.urlCanalOficial = urlCanalOficial;
        this.textoLegal = textoLegal;
        this.atualizadoEm = agora;
    }
}
