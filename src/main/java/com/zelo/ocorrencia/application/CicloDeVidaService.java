package com.zelo.ocorrencia.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.ocorrencia.application.Visoes.ConteudoModeravel;
import com.zelo.ocorrencia.application.Visoes.TipoDeConteudo;
import com.zelo.ocorrencia.domain.CalculadoraDePrioridade;
import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia.DadosDePrioridade;
import com.zelo.ocorrencia.infra.OcorrenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mudanças no ciclo de vida (RF14-RF16) e as operações que a moderação executa sobre ocorrências.
 * As regras ficam no agregado; aqui só há orquestração, auditoria e persistência.
 */
@Service
@RequiredArgsConstructor
public class CicloDeVidaService {

    private final OcorrenciaRepository repository;
    private final ConsultasDeOcorrencia consultas;
    private final CalculadoraDePrioridade calculadoraDePrioridade;
    private final PublicadorDeEventos eventos;
    private final ProvedorDeAtor provedorDeAtor;
    private final AuditoriaService auditoria;
    private final OcorrenciaProperties properties;
    private final Clock clock;

    /** Transição feita pela equipe (moderador, gestor ou admin). */
    @Transactional
    public void transicionar(UUID ocorrenciaId, StatusOcorrencia novo, String motivo, UUID usuarioId) {
        Ator ator = provedorDeAtor.obterParticipante(usuarioId);
        Ocorrencia o = carregar(ocorrenciaId);
        StatusOcorrencia anterior = o.getStatus();
        o.transicionar(novo, ator, motivo, clock.instant());
        eventos.publicar(o);
        Map<String, Object> detalhes = new HashMap<>();
        detalhes.put("de", anterior.name());
        detalhes.put("para", novo.name());
        if (motivo != null && !motivo.isBlank()) {
            detalhes.put("motivo", motivo.trim());
        }
        auditoria.registrar(ator, "STATUS_ALTERADO", "OCORRENCIA", ocorrenciaId, detalhes);
    }

    @Transactional
    public void contestarResolucao(UUID ocorrenciaId, String motivo, UUID usuarioId) {
        provedorDeAtor.obterParticipante(usuarioId);
        Ocorrencia o = carregar(ocorrenciaId);
        o.contestarResolucao(usuarioId, motivo, properties.prazoContestacao(), clock.instant());
        eventos.publicar(o);
    }

    @Transactional
    public void confirmarResolucao(UUID ocorrenciaId, UUID usuarioId) {
        provedorDeAtor.obter(usuarioId);
        Ocorrencia o = carregar(ocorrenciaId);
        o.confirmarResolucao(usuarioId, properties.prazoContestacao(), clock.instant());
        eventos.publicar(o);
    }

    // ------------------------------------------------------------------ API para o módulo de moderação

    @Transactional(readOnly = true)
    public ConteudoModeravel conteudo(UUID ocorrenciaId) {
        Ocorrencia o = repository.findById(ocorrenciaId).orElseThrow(RegistroDeOcorrenciaService::naoEncontrada);
        return new ConteudoModeravel(TipoDeConteudo.OCORRENCIA, o.getId(), o.getId(), o.getCidadeId(),
                o.getAutorId(), o.getDescricao(), o.estaRemovida(), o.getStatus());
    }

    @Transactional
    public void removerPelaModeracao(UUID ocorrenciaId, Ator ator, String motivo) {
        Ocorrencia o = carregar(ocorrenciaId);
        o.removerPelaModeracao(ator, motivo, clock.instant());
        eventos.publicar(o);
        auditoria.registrar(ator, "OCORRENCIA_REMOVIDA", "OCORRENCIA", ocorrenciaId, Map.of("motivo", motivo));
    }

    /**
     * Regra 3.3 / RF19: a duplicada passa a apontar para a principal, e quem apoiava a duplicada
     * (curtidas e o próprio autor) passa a apoiar a principal.
     */
    @Transactional
    public void mesclar(UUID duplicadaId, UUID principalId, Ator ator, String motivo) {
        Instant agora = clock.instant();
        Ocorrencia duplicada = carregar(duplicadaId);
        Ocorrencia principal = carregar(principalId);
        duplicada.marcarComoDuplicada(principal, ator, motivo, agora);
        eventos.publicar(duplicada);
        consultas.migrarApoios(duplicadaId, principalId, duplicada.getAutorId(), principal.getAutorId(), agora);
        consultas.sincronizarCurtidas(principalId, agora);
        DadosDePrioridade dados = consultas.dadosDePrioridade(principalId);
        consultas.atualizarPrioridade(principalId,
                calculadoraDePrioridade.calcular(dados.totalCurtidas(), dados.criadaEm(), agora));
        auditoria.registrar(ator, "OCORRENCIA_MESCLADA", "OCORRENCIA", duplicadaId,
                Map.of("principalId", principalId, "motivo", motivo));
    }

    private Ocorrencia carregar(UUID id) {
        Ocorrencia o = repository.findById(id).orElseThrow(RegistroDeOcorrenciaService::naoEncontrada);
        o.exigirNaoRemovida();
        return o;
    }
}
