package com.zelo.notificacao.application;

import com.zelo.notificacao.domain.Notificacao.Tipo;
import com.zelo.ocorrencia.application.ConsultaDeOcorrenciaService;
import com.zelo.usuario.application.UsuarioService;
import com.zelo.usuario.application.UsuarioService.Gestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** Regra 3.6: o gestor recebe um resumo diário das novas ocorrências da cidade em que atua. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumoDiarioDoGestor {

    private final UsuarioService usuarios;
    private final ConsultaDeOcorrenciaService ocorrencias;
    private final NotificacaoService notificacoes;
    private final Clock clock;

    @Scheduled(cron = "${zelo.ocorrencia.rotinas.cron-resumo-gestor}", zone = "${zelo.ocorrencia.fuso-horario}")
    public void enviar() {
        Instant desde = clock.instant().minus(Duration.ofDays(1));
        int enviados = 0;
        for (Gestor gestor : usuarios.listarGestoresAtivos()) {
            try {
                long novas = ocorrencias.contarNovas(gestor.cidadeId(), desde);
                if (novas > 0) {
                    notificacoes.notificar(Set.of(gestor.id()), Tipo.RESUMO_GESTOR, "Resumo diário",
                            u -> novas + " nova(s) ocorrência(s) na sua área nas últimas 24 horas.", null);
                    enviados++;
                }
            } catch (RuntimeException e) {
                log.error("Falha ao gerar o resumo do gestor {}", gestor.id(), e);
            }
        }
        log.info("Resumo diário enviado a {} gestor(es).", enviados);
    }
}
