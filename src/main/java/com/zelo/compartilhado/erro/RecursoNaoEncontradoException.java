package com.zelo.compartilhado.erro;

/** Recurso inexistente ou indisponível para o usuário. HTTP 404. */
public class RecursoNaoEncontradoException extends ErroDeNegocioException {

    public RecursoNaoEncontradoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public RecursoNaoEncontradoException(String mensagem) {
        super("RECURSO_NAO_ENCONTRADO", mensagem);
    }
}
