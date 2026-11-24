package com.zelo.notificacao.infra;

import com.zelo.notificacao.domain.PreferenciaNotificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PreferenciaNotificacaoRepository extends JpaRepository<PreferenciaNotificacao, UUID> {
}
