package com.zelo.compartilhado.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Envelope de paginação estável, independente da serialização de {@link Page}. */
public record PaginaResposta<T>(List<T> itens, int pagina, int tamanho, long totalItens, int totalPaginas) {

    public static <T> PaginaResposta<T> de(Page<T> pagina) {
        return new PaginaResposta<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }

    public static <E, T> PaginaResposta<T> de(Page<E> pagina, Function<E, T> conversor) {
        return de(pagina.map(conversor));
    }
}
