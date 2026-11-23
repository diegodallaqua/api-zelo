package com.zelo.compartilhado.erro;

/** Limite de uso excedido (rate limit ou limite diário). HTTP 429. */
public class LimiteExcedidoException extends ErroDeNegocioException {

    public LimiteExcedidoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public LimiteExcedidoException(String mensagem) {
        super("LIMITE_EXCEDIDO", mensagem);
    }
}
