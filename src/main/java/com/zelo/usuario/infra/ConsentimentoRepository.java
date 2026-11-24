package com.zelo.usuario.infra;

import com.zelo.usuario.domain.Consentimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsentimentoRepository extends JpaRepository<Consentimento, UUID> {

    List<Consentimento> findByUsuarioIdOrderByRegistradoEmDesc(UUID usuarioId);
}
