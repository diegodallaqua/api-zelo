package com.zelo.ocorrencia.domain;

import com.zelo.compartilhado.erro.ConflitoException;

/** Transição inexistente no ciclo de vida a partir do status atual. HTTP 409. */
public class TransicaoDeStatusInvalidaException extends ConflitoException {

    public TransicaoDeStatusInvalidaException(StatusOcorrencia de, StatusOcorrencia para) {
        super("TRANSICAO_DE_STATUS_INVALIDA",
                "Não é possível alterar uma ocorrência de " + de + " para " + para + ".");
    }
}
