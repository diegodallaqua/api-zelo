package com.zelo.compartilhado.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    @Query("""
            select r from RegistroAuditoria r
            where (:entidade is null or r.entidade = :entidade)
              and (:entidadeId is null or r.entidadeId = :entidadeId)
              and (:atorId is null or r.atorId = :atorId)
            order by r.ocorridoEm desc, r.id desc
            """)
    Page<RegistroAuditoria> pesquisar(@Param("entidade") String entidade,
                                      @Param("entidadeId") String entidadeId,
                                      @Param("atorId") UUID atorId,
                                      Pageable pageable);
}
