package com.zelo.ocorrencia.application;

import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Filtros das listagens de ocorrências (RF06): cidade, categoria, status e período, com ordenação.
 * Usado pela listagem pública e pelo painel do gestor.
 */
public record FiltroDeOcorrencias(Integer cidadeId, UUID categoriaId, Set<StatusOcorrencia> status,
                                  java.time.LocalDate de, java.time.LocalDate ate, Ordenacao ordenacao) {

    public enum Ordenacao {
        PRIORIDADE(Sort.by(Sort.Order.desc("prioridade"), Sort.Order.asc("criadaEm"))),
        MAIS_RECENTES(Sort.by(Sort.Order.desc("criadaEm"))),
        MAIS_ANTIGAS(Sort.by(Sort.Order.asc("criadaEm"))),
        MAIS_CURTIDAS(Sort.by(Sort.Order.desc("totalCurtidas"), Sort.Order.asc("criadaEm"))),
        SEM_ATUALIZACAO_HA_MAIS_TEMPO(Sort.by(Sort.Order.asc("ultimaAtividadeEm")));

        private final Sort sort;

        Ordenacao(Sort sort) {
            this.sort = sort;
        }

        public Sort sort() {
            return sort;
        }
    }

    public FiltroDeOcorrencias comCidade(Integer cidade) {
        return new FiltroDeOcorrencias(cidade, categoriaId, status, de, ate, ordenacao);
    }

    public Ordenacao ordenacaoOuPadrao(Ordenacao padrao) {
        return ordenacao == null ? padrao : ordenacao;
    }

    /** @param periodo início inclusivo e fim exclusivo, já convertidos do fuso configurado (podem ser nulos) */
    Specification<Ocorrencia> especificacao(Set<StatusOcorrencia> statusPadrao, Instant[] periodo) {
        Set<StatusOcorrencia> statusEfetivo = status == null || status.isEmpty() ? statusPadrao : status;
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isNull(root.get("removidaEm")));
            if (cidadeId != null) {
                p.add(cb.equal(root.get("cidadeId"), cidadeId));
            }
            if (categoriaId != null) {
                p.add(cb.equal(root.get("categoriaId"), categoriaId));
            }
            if (statusEfetivo != null && !statusEfetivo.isEmpty()) {
                p.add(root.get("status").in(statusEfetivo));
            }
            if (periodo[0] != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("criadaEm"), periodo[0]));
            }
            if (periodo[1] != null) {
                p.add(cb.lessThan(root.get("criadaEm"), periodo[1]));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
