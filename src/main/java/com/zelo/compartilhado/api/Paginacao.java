package com.zelo.compartilhado.api;

import com.zelo.compartilhado.erro.RequisicaoInvalidaException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Validação dos parâmetros {@code pagina} e {@code tamanho} usados nas listagens. */
public final class Paginacao {

    public static final int TAMANHO_MAXIMO = 100;

    private Paginacao() {
    }

    public static Pageable de(int pagina, int tamanho, Sort ordenacao) {
        if (pagina < 0) {
            throw new RequisicaoInvalidaException("PAGINACAO_INVALIDA", "O parâmetro 'pagina' deve ser maior ou igual a 0.");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new RequisicaoInvalidaException("PAGINACAO_INVALIDA",
                    "O parâmetro 'tamanho' deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        }
        return PageRequest.of(pagina, tamanho, ordenacao);
    }

    public static Pageable de(int pagina, int tamanho) {
        return de(pagina, tamanho, Sort.unsorted());
    }
}
