package com.zelo.compartilhado.erro;

/** Dependência externa indisponível (armazenamento, IBGE). HTTP 503. */
public class ServicoIndisponivelException extends ErroDeNegocioException {

    public ServicoIndisponivelException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public ServicoIndisponivelException(String mensagem) {
        super("SERVICO_INDISPONIVEL", mensagem);
    }
}
