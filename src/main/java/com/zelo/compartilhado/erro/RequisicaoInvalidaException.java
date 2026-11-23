package com.zelo.compartilhado.erro;

/** Dados de entrada inválidos detectados fora da validação declarativa. HTTP 400. */
public class RequisicaoInvalidaException extends ErroDeNegocioException {

    public RequisicaoInvalidaException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public RequisicaoInvalidaException(String mensagem) {
        super("REQUISICAO_INVALIDA", mensagem);
    }
}
