package com.zelo.compartilhado.erro;

import java.util.Map;

/**
 * Base das exceções de negócio. Não conhece HTTP: o {@link ApiExceptionHandler} traduz cada subtipo
 * para o status adequado. O {@code codigo} é estável e serve para o cliente tratar o erro sem
 * depender do texto da mensagem.
 */
public abstract class ErroDeNegocioException extends RuntimeException {

    private final String codigo;

    protected ErroDeNegocioException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    /** Propriedades extras incluídas no corpo da resposta de erro. */
    public Map<String, Object> getPropriedades() {
        return Map.of();
    }
}
