package com.zelo.categoria.infra;

import com.zelo.categoria.domain.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

    List<Categoria> findByAtivaTrueOrderByNomeAsc();

    List<Categoria> findAllByOrderByNomeAsc();

    @Query("""
            select case when count(c) > 0 then true else false end from Categoria c
            where lower(c.nome) = lower(:nome) and (:ignorarId is null or c.id <> :ignorarId)
            """)
    boolean existeComNome(@Param("nome") String nome, @Param("ignorarId") UUID ignorarId);
}
