package com.zelo.usuario.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "zelo")
public record UsuarioProperties(Cadastro cadastro, AdminInicial adminInicial) {

    public record Cadastro(List<String> dominiosEmailPermitidos, String versaoTermosDeUso,
                           String versaoPoliticaDePrivacidade, String versaoConsentimentoLocalizacao) {
    }

    public record AdminInicial(String nome, String email, String senha, EnderecoInicial endereco) {
    }

    /** @param cidadeId código IBGE da cidade */
    public record EnderecoInicial(String cep, String rua, String bairro, String numero, String complemento,
                                  Integer cidadeId) {
    }
}
