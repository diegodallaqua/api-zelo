package com.zelo.compartilhado.erro;

/** A operação conflita com o estado atual do recurso. HTTP 409. */
public class ConflitoException extends ErroDeNegocioException {

    public ConflitoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public ConflitoException(String mensagem) {
        super("CONFLITO", mensagem);
    }
}
