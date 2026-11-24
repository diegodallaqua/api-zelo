package com.zelo.compartilhado.erro;

/** Requisição bem formada, mas que viola uma regra de negócio. HTTP 422. */
public class RegraDeNegocioException extends ErroDeNegocioException {

    public RegraDeNegocioException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public RegraDeNegocioException(String mensagem) {
        super("REGRA_DE_NEGOCIO_VIOLADA", mensagem);
    }
}
