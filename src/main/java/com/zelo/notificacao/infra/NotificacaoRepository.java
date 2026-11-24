package com.zelo.notificacao.infra;

import com.zelo.notificacao.domain.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    Page<Notificacao> findByUsuarioIdOrderByCriadaEmDesc(UUID usuarioId, Pageable pageable);

    Page<Notificacao> findByUsuarioIdAndLidaEmIsNullOrderByCriadaEmDesc(UUID usuarioId, Pageable pageable);

    long countByUsuarioIdAndLidaEmIsNull(UUID usuarioId);

    @Modifying
    @Query("update Notificacao n set n.lidaEm = :agora where n.usuarioId = :usuarioId and n.lidaEm is null")
    int marcarTodasComoLidas(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);

    @Modifying
    @Query("delete from Notificacao n where n.usuarioId = :usuarioId")
    void excluirDoUsuario(@Param("usuarioId") UUID usuarioId);
}
