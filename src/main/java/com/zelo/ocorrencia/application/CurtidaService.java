package com.zelo.ocorrencia.application;

import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.ocorrencia.application.Visoes.CurtidaVisao;
import com.zelo.ocorrencia.domain.CalculadoraDePrioridade;
import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia;
import com.zelo.ocorrencia.infra.OcorrenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** "Também vejo esse problema" (RF11): uma curtida por usuário, com contador atualizado de forma atômica. */
@Service
@RequiredArgsConstructor
public class CurtidaService {

    private final OcorrenciaRepository repository;
    private final ConsultasDeOcorrencia consultas;
    private final CalculadoraDePrioridade calculadoraDePrioridade;
    private final ProvedorDeAtor provedorDeAtor;
    private final Clock clock;

    @Transactional
    public CurtidaVisao curtir(UUID ocorrenciaId, UUID usuarioId) {
        provedorDeAtor.obterParticipante(usuarioId);
        Ocorrencia o = repository.findById(ocorrenciaId).orElseThrow(RegistroDeOcorrenciaService::naoEncontrada);
        o.exigirPodeReceberCurtidaDe(usuarioId);
        Instant agora = clock.instant();
        if (!consultas.inserirCurtida(ocorrenciaId, usuarioId, agora)) {
            throw new ConflitoException("CURTIDA_EXISTENTE", "Você já curtiu esta ocorrência.");
        }
        return atualizarContadores(o, agora, true);
    }

    @Transactional
    public CurtidaVisao descurtir(UUID ocorrenciaId, UUID usuarioId) {
        provedorDeAtor.obter(usuarioId);
        Ocorrencia o = repository.findById(ocorrenciaId).orElseThrow(RegistroDeOcorrenciaService::naoEncontrada);
        o.exigirNaoRemovida();
        if (!consultas.removerCurtida(ocorrenciaId, usuarioId)) {
            throw new RecursoNaoEncontradoException("CURTIDA_NAO_ENCONTRADA", "Você ainda não curtiu esta ocorrência.");
        }
        return atualizarContadores(o, clock.instant(), false);
    }

    private CurtidaVisao atualizarContadores(Ocorrencia o, Instant agora, boolean curtida) {
        int total = consultas.sincronizarCurtidas(o.getId(), agora);
        consultas.atualizarPrioridade(o.getId(), calculadoraDePrioridade.calcular(total, o.getCriadaEm(), agora));
        return new CurtidaVisao(o.getId(), total, curtida);
    }
}
