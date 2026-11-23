package com.zelo.compartilhado.erro;

/** Arquivo enviado recusado. O status HTTP depende do {@link Motivo}. */
public class ArquivoInvalidoException extends ErroDeNegocioException {

    public enum Motivo {
        /** HTTP 415. */
        FORMATO_NAO_SUPORTADO,
        /** HTTP 413. */
        TAMANHO_EXCEDIDO,
        /** HTTP 422: arquivo corrompido ou com dimensões fora do limite. */
        CONTEUDO_INVALIDO
    }

    private final Motivo motivo;

    public ArquivoInvalidoException(Motivo motivo, String mensagem) {
        super("ARQUIVO_" + motivo.name(), mensagem);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
