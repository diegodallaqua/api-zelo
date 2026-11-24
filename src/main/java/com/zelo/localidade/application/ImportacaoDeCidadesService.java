package com.zelo.localidade.application;

import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.localidade.infra.CidadeRepository;
import com.zelo.localidade.infra.ClienteIbge;
import com.zelo.localidade.infra.ClienteIbge.CidadeIbge;
import com.zelo.localidade.infra.ClienteIbge.EstadoIbge;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Importa (ou atualiza) estados e cidades, com o polígono de cada cidade, a partir do IBGE.
 * Na primeira inicialização (tabela vazia) roda de forma síncrona, antes da criação do administrador
 * inicial, que precisa de uma cidade no endereço. Depois, o admin pode disparar a atualização em segundo plano.
 * Cidade sem malha publicada é ignorada e listada no estado. Preserva {@code ativo} e os textos configurados.
 */
@Slf4j
@Service
@Order(10)
public class ImportacaoDeCidadesService implements ApplicationRunner {

    private static final String UPSERT_ESTADO = """
            INSERT INTO estado (id, nome) VALUES (?, ?)
            ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome
            """;

    private static final String UPSERT_CIDADE = """
            INSERT INTO cidade (id, nome, estado_id, limite, criado_em, atualizado_em)
            VALUES (?, ?, ?,
                    ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)), 3)),
                    now(), now())
            ON CONFLICT (id) DO UPDATE
                SET nome = EXCLUDED.nome, estado_id = EXCLUDED.estado_id, limite = EXCLUDED.limite, atualizado_em = now()
            """;

    public enum Situacao { NUNCA_EXECUTADA, EM_ANDAMENTO, CONCLUIDA, FALHOU }

    public record EstadoImportacao(Situacao situacao, Instant iniciadaEm, Instant concluidaEm, int estadosProcessados,
                                   int cidadesImportadas, List<Integer> cidadesSemMalha, String erro) {

        boolean emAndamento() {
            return situacao == Situacao.EM_ANDAMENTO;
        }
    }

    private final ClienteIbge ibge;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final CidadeRepository cidades;
    private final LocalidadeProperties properties;
    private final Clock clock;
    private final AtomicReference<EstadoImportacao> estado = new AtomicReference<>(
            new EstadoImportacao(Situacao.NUNCA_EXECUTADA, null, null, 0, 0, List.of(), null));
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "importacao-cidades");
        thread.setDaemon(true);
        return thread;
    });

    public ImportacaoDeCidadesService(ClienteIbge ibge, JdbcTemplate jdbc, TransactionTemplate transacao,
                                      CidadeRepository cidades, LocalidadeProperties properties, Clock clock) {
        this.ibge = ibge;
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.cidades = cidades;
        this.properties = properties;
        this.clock = clock;
    }

    /** Primeira inicialização: importa de forma síncrona se ainda não houver cidades. */
    @Override
    public void run(ApplicationArguments args) {
        if (properties.ibge().importarNaInicializacao() && cidades.count() == 0) {
            log.info("Tabela de cidades vazia: importando estados e cidades do IBGE antes de concluir a inicialização.");
            marcarInicio();
            executar();
        }
    }

    /** Atualização manual pelo administrador, em segundo plano. */
    public EstadoImportacao iniciar() {
        EstadoImportacao novo = marcarInicio();
        executor.submit(this::executar);
        return novo;
    }

    public EstadoImportacao estado() {
        return estado.get();
    }

    private EstadoImportacao marcarInicio() {
        EstadoImportacao atual = estado.get();
        EstadoImportacao novo = new EstadoImportacao(Situacao.EM_ANDAMENTO, clock.instant(), null, 0, 0, List.of(), null);
        if (atual.emAndamento() || !estado.compareAndSet(atual, novo)) {
            throw new ConflitoException("IMPORTACAO_EM_ANDAMENTO", "Já existe uma importação de cidades em andamento.");
        }
        return novo;
    }

    private void executar() {
        Instant inicio = estado.get().iniciadaEm();
        List<Integer> semMalha = new ArrayList<>();
        int importadas = 0;
        int estados = 0;
        try {
            List<Object[]> linhasDeEstado = ibge.listarEstados().stream()
                    .map(e -> new Object[]{e.id(), e.nome()}).toList();
            transacao.executeWithoutResult(s -> jdbc.batchUpdate(UPSERT_ESTADO, linhasDeEstado));

            Map<Integer, List<CidadeIbge>> porEstado = ibge.listarCidades().stream()
                    .collect(Collectors.groupingBy(CidadeIbge::estadoId, TreeMap::new, Collectors.toList()));
            for (Map.Entry<Integer, List<CidadeIbge>> uf : porEstado.entrySet()) {
                Map<Integer, String> malhas = ibge.malhasDasCidadesDoEstado(uf.getKey());
                List<Object[]> lote = new ArrayList<>();
                for (CidadeIbge c : uf.getValue()) {
                    String geometria = malhas.get(c.id());
                    if (geometria == null) {
                        semMalha.add(c.id());
                        continue;
                    }
                    lote.add(new Object[]{c.id(), c.nome(), c.estadoId(), geometria});
                }
                transacao.executeWithoutResult(s -> jdbc.batchUpdate(UPSERT_CIDADE, lote));
                importadas += lote.size();
                estados++;
                estado.set(new EstadoImportacao(Situacao.EM_ANDAMENTO, inicio, null, estados, importadas,
                        List.copyOf(semMalha), null));
                log.info("Cidades importadas do estado {}: {}", uf.getKey(), lote.size());
            }
            estado.set(new EstadoImportacao(Situacao.CONCLUIDA, inicio, clock.instant(), estados, importadas,
                    List.copyOf(semMalha), null));
            log.info("Importação concluída: {} estados, {} cidades, {} sem malha no IBGE {}.",
                    linhasDeEstado.size(), importadas, semMalha.size(), semMalha);
        } catch (RuntimeException e) {
            log.error("Falha na importação de estados e cidades", e);
            estado.set(new EstadoImportacao(Situacao.FALHOU, inicio, clock.instant(), estados, importadas,
                    List.copyOf(semMalha), e.getMessage()));
        }
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }
}
