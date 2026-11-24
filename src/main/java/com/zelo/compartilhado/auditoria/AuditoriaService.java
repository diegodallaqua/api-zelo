package com.zelo.compartilhado.auditoria;

import com.zelo.compartilhado.seguranca.Ator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Registra ações sensíveis na mesma transação da ação: se a ação falhar, o registro também não fica.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    public static final String PAPEL_SISTEMA = "SISTEMA";

    private final RegistroAuditoriaRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Ator ator, String acao, String entidade, Object entidadeId, Map<String, ?> detalhes) {
        salvar(ator != null ? ator.id() : null, ator != null ? ator.perfil().name() : PAPEL_SISTEMA,
                acao, entidade, entidadeId, detalhes);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarPeloSistema(String acao, String entidade, Object entidadeId, Map<String, ?> detalhes) {
        salvar(null, PAPEL_SISTEMA, acao, entidade, entidadeId, detalhes);
    }

    @Transactional(readOnly = true)
    public Page<RegistroAuditoria> pesquisar(String entidade, String entidadeId, UUID atorId, Pageable pageable) {
        return repository.pesquisar(entidade, entidadeId, atorId, pageable);
    }

    private void salvar(UUID atorId, String papel, String acao, String entidade, Object entidadeId,
                        Map<String, ?> detalhes) {
        String json = detalhes == null || detalhes.isEmpty() ? null : jsonMapper.writeValueAsString(detalhes);
        repository.save(new RegistroAuditoria(atorId, papel, acao, entidade, String.valueOf(entidadeId), json,
                clock.instant()));
    }
}
