package com.zelo.ocorrencia.infra;

import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, UUID>, JpaSpecificationExecutor<Ocorrencia> {

    /** Conta inclusive as excluídas, para que excluir e recriar não burle o limite diário. */
    long countByAutorIdAndCriadaEmGreaterThanEqual(UUID autorId, Instant desde);

    Page<Ocorrencia> findByAutorIdAndRemovidaEmIsNullOrderByCriadaEmDesc(UUID autorId, Pageable pageable);

    @Query("""
            select o from Ocorrencia o
            where o.status = :status and o.removidaEm is null
              and o.avisoArquivamentoEm is null and o.ultimaAtividadeEm < :limite
            order by o.ultimaAtividadeEm
            """)
    List<Ocorrencia> paraAvisoDeArquivamento(@Param("status") StatusOcorrencia status,
                                             @Param("limite") Instant limite, Pageable pageable);

    @Query("""
            select o from Ocorrencia o
            where o.status = :status and o.removidaEm is null
              and o.avisoArquivamentoEm is not null and o.avisoArquivamentoEm < :limite
            order by o.avisoArquivamentoEm
            """)
    List<Ocorrencia> paraArquivamento(@Param("status") StatusOcorrencia status, @Param("limite") Instant limite,
                                      Pageable pageable);

    @Query("""
            select count(o) from Ocorrencia o
            where o.cidadeId = :cidadeId and o.removidaEm is null and o.criadaEm >= :desde
            """)
    long contarNovas(@Param("cidadeId") Integer cidadeId, @Param("desde") Instant desde);

    @Modifying
    @Query("update Ocorrencia o set o.autorId = null where o.autorId = :autorId")
    int anonimizarAutor(@Param("autorId") UUID autorId);

    @Modifying
    @Query("update Ocorrencia o set o.autorId = null where o.autorId is not null and o.criadaEm < :limite")
    int anonimizarAutoresAnterioresA(@Param("limite") Instant limite);
}
