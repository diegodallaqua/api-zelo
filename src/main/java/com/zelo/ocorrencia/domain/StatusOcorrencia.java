package com.zelo.ocorrencia.domain;

import java.util.EnumSet;
import java.util.Set;

/** Ciclo de vida (seção 2.2). "Contestada" não é status: é um evento que reabre a ocorrência. */
public enum StatusOcorrencia {
    EM_ANALISE,
    ABERTA,
    EM_ANDAMENTO,
    RESOLVIDA,
    REJEITADA,
    DUPLICADA,
    ARQUIVADA;

    /** Status em que o problema ainda está pendente: participam da busca de duplicidade e aceitam curtidas. */
    public static final Set<StatusOcorrencia> ATIVOS = EnumSet.of(EM_ANALISE, ABERTA, EM_ANDAMENTO);

    /** Exibidos no mapa quando o filtro de status não é informado. */
    public static final Set<StatusOcorrencia> VISIVEIS_NO_MAPA_POR_PADRAO = EnumSet.of(ABERTA, EM_ANDAMENTO, RESOLVIDA);

    public boolean ativo() {
        return ATIVOS.contains(this);
    }

    /** RF12: o autor edita ou exclui a própria ocorrência enquanto ela estiver aberta. */
    public boolean editavelPeloAutor() {
        return this == EM_ANALISE || this == ABERTA;
    }

    public boolean aceitaComentarios() {
        return this != DUPLICADA && this != ARQUIVADA;
    }
}
