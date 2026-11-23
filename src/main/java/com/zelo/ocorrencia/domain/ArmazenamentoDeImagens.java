package com.zelo.ocorrencia.domain;

/**
 * Onde as fotos ficam guardadas. Implementações: disco local (desenvolvimento) e Cloudflare R2.
 * Trocar de provedor não afeta as regras de negócio.
 */
public interface ArmazenamentoDeImagens {

    void salvar(String chave, byte[] conteudo, String contentType);

    void remover(String chave);

    String urlPublica(String chave);
}
