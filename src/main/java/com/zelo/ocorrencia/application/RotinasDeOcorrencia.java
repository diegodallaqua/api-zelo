package com.zelo.ocorrencia.application;

import com.zelo.ocorrencia.domain.CalculadoraDePrioridade;
import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import com.zelo.ocorrencia.infra.ComentarioRepository;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia.DadosDePrioridade;
import com.zelo.ocorrencia.infra.OcorrenciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Rotinas diárias: arquivamento por inatividade (3.4), retenção/anonimização (3.7) e prioridade. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RotinasDeOcorrencia {

    private static final int LOTE = 200;

    private final OcorrenciaRepository repository;
    private final ComentarioRepository comentarios;
    private final ConsultasDeOcorrencia consultas;
    private final CalculadoraDePrioridade calculadoraDePrioridade;
    private final PublicadorDeEventos eventos;
    private final OcorrenciaProperties properties;
    private final TransactionTemplate transacao;
    private final Clock clock;

    /** Avisa o autor após N dias sem atividade e arquiva M dias depois do aviso, se nada mudar. */
    @Scheduled(cron = "${zelo.ocorrencia.rotinas.cron-arquivamento}", zone = "${zelo.ocorrencia.fuso-horario}")
    public void arquivarInativas() {
        Instant agora = clock.instant();
        Instant limiteAviso = agora.minus(Duration.ofDays(properties.diasSemAtividadeParaAviso()));
        Instant limiteArquivamento = agora.minus(Duration.ofDays(properties.diasAposAvisoParaArquivar()));
        int avisadas = processarEmLotes(() -> repository.paraAvisoDeArquivamento(StatusOcorrencia.ABERTA, limiteAviso,
                PageRequest.of(0, LOTE)), o -> o.avisarArquivamento(agora));
        int arquivadas = processarEmLotes(() -> repository.paraArquivamento(StatusOcorrencia.ABERTA, limiteArquivamento,
                PageRequest.of(0, LOTE)), o -> o.arquivarPorInatividade(agora));
        log.info("Rotina de arquivamento: {} aviso(s) emitido(s), {} ocorrência(s) arquivada(s).", avisadas, arquivadas);
    }

    /** Política de retenção: desvincula o autor de ocorrências e comentários antigos. */
    @Scheduled(cron = "${zelo.ocorrencia.rotinas.cron-retencao}", zone = "${zelo.ocorrencia.fuso-horario}")
    public void aplicarRetencao() {
        Instant limite = ZonedDateTime.now(clock.withZone(properties.fusoHorario()))
                .minusYears(properties.anosRetencaoAutor()).toInstant();
        int[] totais = transacao.execute(s -> new int[]{
                repository.anonimizarAutoresAnterioresA(limite),
                comentarios.anonimizarAutoresAnterioresA(limite)});
        if (totais != null) {
            log.info("Rotina de retenção: {} ocorrência(s) e {} comentário(s) anonimizados.", totais[0], totais[1]);
        }
    }

    /** A prioridade cresce com os dias em aberto, então é recalculada diariamente. */
    @Scheduled(cron = "${zelo.ocorrencia.rotinas.cron-prioridade}", zone = "${zelo.ocorrencia.fuso-horario}")
    public void recalcularPrioridades() {
        Instant agora = clock.instant();
        UUID ultimo = null;
        int total = 0;
        List<DadosDePrioridade> lote;
        do {
            lote = consultas.dadosDePrioridadeDosAtivos(ultimo, 500);
            for (DadosDePrioridade d : lote) {
                consultas.atualizarPrioridade(d.id(), calculadoraDePrioridade.calcular(d.totalCurtidas(), d.criadaEm(), agora));
            }
            if (!lote.isEmpty()) {
                ultimo = lote.get(lote.size() - 1).id();
                total += lote.size();
            }
        } while (lote.size() == 500);
        log.info("Rotina de prioridade: {} ocorrência(s) recalculada(s).", total);
    }

    /** Processa lotes até a consulta não retornar mais nada (cada item sai do critério ao ser processado). */
    private int processarEmLotes(java.util.function.Supplier<List<Ocorrencia>> consulta,
                                 java.util.function.Consumer<Ocorrencia> acao) {
        int processadas = 0;
        // Trava de segurança contra laço infinito caso algum item não saia do critério da consulta.
        for (int lotes = 0; lotes < 10_000; lotes++) {
            Integer quantidade = transacao.execute(s -> {
                List<Ocorrencia> lote = consulta.get();
                lote.forEach(o -> {
                    acao.accept(o);
                    eventos.publicar(o);
                });
                return lote.size();
            });
            if (quantidade == null || quantidade == 0) {
                return processadas;
            }
            processadas += quantidade;
            if (quantidade < LOTE) {
                return processadas;
            }
        }
        log.warn("Rotina interrompida pela trava de segurança após {} itens.", processadas);
        return processadas;
    }
}
