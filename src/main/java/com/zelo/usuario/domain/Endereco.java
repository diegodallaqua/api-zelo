package com.zelo.usuario.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Endereço (1:N com usuário). Para moderadores e gestores, a cidade do endereço define a área de atuação.
 */
@Entity
@Table(name = "endereco")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String cep;
    private String rua;
    private String bairro;
    private String numero;
    private String complemento;
    private Integer cidadeId;
    private Instant criadoEm;
    private Instant atualizadoEm;

    /** Dados de entrada já validados. O CEP é normalizado para 8 dígitos. */
    public record Dados(String cep, String rua, String bairro, String numero, String complemento, Integer cidadeId) {

        public Dados {
            Objects.requireNonNull(cidadeId, "cidadeId");
            cep = cep == null ? null : cep.replaceAll("\\D", "");
            rua = rua == null ? null : rua.trim();
            bairro = bairro == null ? null : bairro.trim();
            numero = numero == null ? null : numero.trim();
            complemento = complemento == null || complemento.isBlank() ? null : complemento.trim();
        }
    }

    public Endereco(Dados dados, Instant agora) {
        aplicar(dados);
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void atualizar(Dados dados, Instant agora) {
        aplicar(dados);
        this.atualizadoEm = agora;
    }

    private void aplicar(Dados d) {
        this.cep = d.cep();
        this.rua = d.rua();
        this.bairro = d.bairro();
        this.numero = d.numero();
        this.complemento = d.complemento();
        this.cidadeId = d.cidadeId();
    }
}
