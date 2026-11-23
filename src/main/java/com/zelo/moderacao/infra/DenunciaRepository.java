package com.zelo.moderacao.infra;

import com.zelo.moderacao.domain.Denuncia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DenunciaRepository extends JpaRepository<Denuncia, UUID> {

    /** Já existe outra denúncia do mesmo usuário para o mesmo conteúdo? */
    @Query("""
            select case when count(d) > 0 then true else false end from Denuncia d
            where d.denuncianteId = :denuncianteId
              and ((:ocorrenciaId is not null and d.ocorrenciaId = :ocorrenciaId)
                or (:comentarioId is not null and d.comentarioId = :comentarioId))
              and (:ignorarId is null or d.id <> :ignorarId)
            """)
    boolean existeDoUsuario(@Param("denuncianteId") UUID denuncianteId, @Param("ocorrenciaId") UUID ocorrenciaId,
                            @Param("comentarioId") UUID comentarioId, @Param("ignorarId") UUID ignorarId);

    List<Denuncia> findByOcorrenciaIdAndStatus(UUID ocorrenciaId, Denuncia.Status status);

    List<Denuncia> findByComentarioIdAndStatus(UUID comentarioId, Denuncia.Status status);

    List<Denuncia> findByOcorrenciaIdOrderByCriadaEmDesc(UUID ocorrenciaId);

    List<Denuncia> findByComentarioIdOrderByCriadaEmDesc(UUID comentarioId);
}
