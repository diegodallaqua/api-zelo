package com.zelo.categoria.application;

import com.zelo.categoria.domain.Categoria;
import com.zelo.categoria.infra.CategoriaRepository;
import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Ator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** CRUD de categorias e API pública do módulo para os demais módulos. */
@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository repository;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public record CategoriaResumo(UUID id, String nome, String icone) {
    }

    @Transactional(readOnly = true)
    public List<Categoria> listarAtivas() {
        return repository.findByAtivaTrueOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public List<Categoria> listarTodas() {
        return repository.findAllByOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public Categoria obter(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CATEGORIA_NAO_ENCONTRADA",
                        "Categoria não encontrada: " + id + "."));
    }

    /** Valida a categoria escolhida para uma ocorrência. */
    @Transactional(readOnly = true)
    public void exigirDisponivel(UUID id) {
        Categoria categoria = repository.findById(id)
                .orElseThrow(() -> new RegraDeNegocioException("CATEGORIA_INVALIDA", "A categoria informada não existe."));
        if (!categoria.isAtiva()) {
            throw new RegraDeNegocioException("CATEGORIA_INATIVA",
                    "A categoria '" + categoria.getNome() + "' está desativada.");
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, CategoriaResumo> resumos(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .map(c -> new CategoriaResumo(c.getId(), c.getNome(), c.getIcone()))
                .collect(Collectors.toMap(CategoriaResumo::id, Function.identity()));
    }

    @Transactional
    public Categoria criar(String nome, String descricao, String icone, Ator ator) {
        String nomeLimpo = nome.trim();
        exigirNomeLivre(nomeLimpo, null);
        Categoria categoria = repository.save(new Categoria(nomeLimpo, descricao, icone, clock.instant()));
        auditoria.registrar(ator, "CATEGORIA_CRIADA", "CATEGORIA", categoria.getId(), Map.of("nome", nomeLimpo));
        return categoria;
    }

    @Transactional
    public Categoria atualizar(UUID id, String nome, String descricao, String icone, boolean ativa, Ator ator) {
        Categoria categoria = obter(id);
        String nomeLimpo = nome.trim();
        exigirNomeLivre(nomeLimpo, id);
        categoria.atualizar(nomeLimpo, descricao, icone, ativa, clock.instant());
        auditoria.registrar(ator, "CATEGORIA_ATUALIZADA", "CATEGORIA", id, Map.of("nome", nomeLimpo, "ativa", ativa));
        return categoria;
    }

    /** Só exclui categoria nunca usada; as usadas devem ser desativadas para preservar o histórico. */
    @Transactional
    public void excluir(UUID id, Ator ator) {
        Categoria categoria = obter(id);
        try {
            repository.delete(categoria);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("CATEGORIA_EM_USO",
                    "A categoria já foi usada em ocorrências. Desative-a em vez de excluir.");
        }
        auditoria.registrar(ator, "CATEGORIA_EXCLUIDA", "CATEGORIA", id, Map.of("nome", categoria.getNome()));
    }

    private void exigirNomeLivre(String nome, UUID ignorarId) {
        if (repository.existeComNome(nome, ignorarId)) {
            throw new ConflitoException("CATEGORIA_DUPLICADA", "Já existe uma categoria chamada '" + nome + "'.");
        }
    }
}
