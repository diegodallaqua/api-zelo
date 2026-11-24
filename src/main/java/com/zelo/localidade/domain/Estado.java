package com.zelo.localidade.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Unidade da federação. O id é o código IBGE da UF. Gravado só pela importação do IBGE. */
@Entity
@Table(name = "estado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Estado {

    @Id
    private Integer id;

    private String nome;
}
