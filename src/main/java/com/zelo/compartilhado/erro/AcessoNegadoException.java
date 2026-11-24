package com.zelo.compartilhado.erro;

/** Usuário autenticado sem permissão para a operação. HTTP 403. */
public class AcessoNegadoException extends ErroDeNegocioException {

    public AcessoNegadoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public AcessoNegadoException(String mensagem) {
        super("ACESSO_NEGADO", mensagem);
    }
}
