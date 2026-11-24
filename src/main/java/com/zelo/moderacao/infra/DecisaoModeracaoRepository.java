package com.zelo.moderacao.infra;

import com.zelo.moderacao.domain.DecisaoModeracao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DecisaoModeracaoRepository extends JpaRepository<DecisaoModeracao, UUID> {

    List<DecisaoModeracao> findByUsuarioAfetadoIdOrderByDecididaEmDesc(UUID usuarioId);
}
