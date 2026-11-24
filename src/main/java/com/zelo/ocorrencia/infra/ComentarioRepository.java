package com.zelo.ocorrencia.infra;

import com.zelo.ocorrencia.domain.Comentario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ComentarioRepository extends JpaRepository<Comentario, UUID> {

    Page<Comentario> findByOcorrenciaIdAndRemovidoEmIsNullOrderByCriadoEmAsc(UUID ocorrenciaId, Pageable pageable);

    long countByOcorrenciaIdAndRemovidoEmIsNull(UUID ocorrenciaId);

    @Modifying
    @Query("update Comentario c set c.autorId = null where c.autorId = :autorId")
    int anonimizarAutor(@Param("autorId") UUID autorId);

    @Modifying
    @Query("update Comentario c set c.autorId = null where c.autorId is not null and c.criadoEm < :limite")
    int anonimizarAutoresAnterioresA(@Param("limite") Instant limite);
}
