package com.zelo.moderacao.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/** Tipo de conteúdo moderável. O {@code id} é a chave da tabela {@code tipo_conteudo} (migração V6). */
public enum TipoConteudo {
    OCORRENCIA(1),
    COMENTARIO(2);

    private final int id;

    TipoConteudo(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static TipoConteudo deId(int id) {
        return Arrays.stream(values()).filter(t -> t.id == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de conteúdo desconhecido: " + id));
    }

    /** Grava o tipo como chave estrangeira para {@code tipo_conteudo.id}. */
    @Converter
    public static class ConversorJpa implements AttributeConverter<TipoConteudo, Integer> {

        @Override
        public Integer convertToDatabaseColumn(TipoConteudo tipo) {
            return tipo == null ? null : tipo.id();
        }

        @Override
        public TipoConteudo convertToEntityAttribute(Integer id) {
            return id == null ? null : deId(id);
        }
    }
}
