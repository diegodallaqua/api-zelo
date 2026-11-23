package com.zelo.notificacao.infra;

import com.zelo.notificacao.domain.Notificador;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Implementação provisória: registra o envio em log. Substituir por FCM/e-mail quando houver provedores. */
@Slf4j
@Component
public class NotificadorEmLog implements Notificador {

    @Override
    public void enviar(Envio envio) {
        if (envio.canais().isEmpty()) {
            return;
        }
        log.info("[notificação {}] usuario={} titulo=\"{}\" ocorrencia={}", envio.canais(), envio.usuarioId(),
                envio.titulo(), envio.ocorrenciaId());
    }
}
