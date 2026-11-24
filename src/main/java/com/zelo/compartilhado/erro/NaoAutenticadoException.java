package com.zelo.compartilhado.erro;

/** Credenciais ausentes, inválidas ou expiradas. HTTP 401. */
public class NaoAutenticadoException extends ErroDeNegocioException {

    public NaoAutenticadoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public NaoAutenticadoException(String mensagem) {
        super("NAO_AUTENTICADO", mensagem);
    }
}
