package com.zelo.ocorrencia.domain;

import com.zelo.compartilhado.seguranca.Perfil;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.zelo.ocorrencia.domain.StatusOcorrencia.ABERTA;
import static com.zelo.ocorrencia.domain.StatusOcorrencia.ARQUIVADA;
import static com.zelo.ocorrencia.domain.StatusOcorrencia.DUPLICADA;
import static com.zelo.ocorrencia.domain.StatusOcorrencia.EM_ANALISE;
import static com.zelo.ocorrencia.domain.StatusOcorrencia.EM_ANDAMENTO;
import static com.zelo.ocorrencia.domain.StatusOcorrencia.REJEITADA;
import static com.zelo.ocorrencia.domain.StatusOcorrencia.RESOLVIDA;

/**
 * Matriz de transições de status feitas pela equipe. O administrador pode executar qualquer transição
 * da matriz. Contestação (Resolvida para Aberta pelo autor) e arquivamento automático são tratados
 * à parte em {@link Ocorrencia}.
 */
final class RegrasDeTransicao {

    private static final Set<Perfil> GESTOR = Set.of(Perfil.GESTOR);
    private static final Set<Perfil> MODERADOR = Set.of(Perfil.MODERADOR);
    private static final Set<Perfil> GESTOR_OU_MODERADOR = Set.of(Perfil.GESTOR, Perfil.MODERADOR);
    private static final Set<Perfil> SOMENTE_ADMIN = Set.of();

    private static final Map<StatusOcorrencia, Map<StatusOcorrencia, Set<Perfil>>> MATRIZ = new EnumMap<>(StatusOcorrencia.class);

    static {
        permitir(EM_ANALISE, ABERTA, MODERADOR);
        permitir(EM_ANALISE, REJEITADA, MODERADOR);
        permitir(EM_ANALISE, DUPLICADA, MODERADOR);
        permitir(ABERTA, EM_ANDAMENTO, GESTOR);
        permitir(ABERTA, REJEITADA, GESTOR_OU_MODERADOR);
        permitir(ABERTA, DUPLICADA, MODERADOR);
        permitir(ABERTA, ARQUIVADA, SOMENTE_ADMIN);
        permitir(EM_ANDAMENTO, RESOLVIDA, GESTOR);
        permitir(EM_ANDAMENTO, ABERTA, GESTOR);
        permitir(EM_ANDAMENTO, REJEITADA, GESTOR_OU_MODERADOR);
        // Reabertura de resolução não confirmada: pelo autor (contestação) ou pelo admin.
        permitir(RESOLVIDA, ABERTA, SOMENTE_ADMIN);
    }

    private RegrasDeTransicao() {
    }

    private static void permitir(StatusOcorrencia de, StatusOcorrencia para, Set<Perfil> perfis) {
        MATRIZ.computeIfAbsent(de, k -> new EnumMap<>(StatusOcorrencia.class)).put(para, perfis);
    }

    static boolean existe(StatusOcorrencia de, StatusOcorrencia para) {
        return MATRIZ.getOrDefault(de, Map.of()).containsKey(para);
    }

    static boolean permitida(StatusOcorrencia de, StatusOcorrencia para, Perfil perfil) {
        if (!existe(de, para)) {
            return false;
        }
        return perfil == Perfil.ADMIN || MATRIZ.get(de).get(para).contains(perfil);
    }
}
