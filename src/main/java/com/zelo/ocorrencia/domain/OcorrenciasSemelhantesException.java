package com.zelo.ocorrencia.domain;

import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.ocorrencia.domain.DetectorDeDuplicidade.Semelhante;

import java.util.List;
import java.util.Map;

/**
 * RF10: há ocorrências semelhantes perto. HTTP 409 com a lista em {@code semelhantes}; o app sugere
 * "Também vejo esse problema" ou reenvia com {@code ignorarSemelhantes=true}.
 */
public class OcorrenciasSemelhantesException extends ConflitoException {

    private final transient List<Semelhante> semelhantes;

    public OcorrenciasSemelhantesException(List<Semelhante> semelhantes) {
        super("OCORRENCIAS_SEMELHANTES",
                "Já existe ocorrência semelhante perto deste ponto. Que tal curtir a existente (\"Também vejo esse problema\")? "
                        + "Para criar mesmo assim, reenvie com ignorarSemelhantes=true.");
        this.semelhantes = List.copyOf(semelhantes);
    }

    @Override
    public Map<String, Object> getPropriedades() {
        return Map.of("semelhantes", semelhantes);
    }
}
