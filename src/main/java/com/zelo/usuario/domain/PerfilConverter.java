package com.zelo.usuario.domain;

import com.zelo.compartilhado.seguranca.Perfil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Grava o perfil como chave estrangeira para {@code perfil_usuario.id}. */
@Converter
public class PerfilConverter implements AttributeConverter<Perfil, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Perfil perfil) {
        return perfil == null ? null : perfil.id();
    }

    @Override
    public Perfil convertToEntityAttribute(Integer id) {
        return id == null ? null : Perfil.deId(id);
    }
}
