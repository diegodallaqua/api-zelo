package com.zelo.localidade.api;

import com.zelo.localidade.application.CidadeService.Limites;
import com.zelo.localidade.domain.Cidade;
import com.zelo.localidade.domain.Estado;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public final class LocalidadeDtos {

    private LocalidadeDtos() {
    }

    public record EstadoResposta(Integer id, String nome) {

        public static EstadoResposta de(Estado e) {
            return new EstadoResposta(e.getId(), e.getNome());
        }
    }

    public record CidadeResposta(Integer id, String nome, EstadoResposta estado, boolean ativo) {

        public static CidadeResposta de(Cidade c) {
            return new CidadeResposta(c.getId(), c.getNome(), EstadoResposta.de(c.getEstado()), c.isAtivo());
        }
    }

    public record CidadeDetalheResposta(Integer id, String nome, EstadoResposta estado, boolean ativo,
                                        String avisoCanalNaoOficial, String urlCanalOficial, String textoLegal,
                                        Limites limites) {
    }

    public record ConfigurarCidadeRequest(
            @NotNull(message = "Informe se a cidade está ativa.") Boolean ativo,
            @Size(max = 2000, message = "O aviso deve ter no máximo 2000 caracteres.") String avisoCanalNaoOficial,
            @Size(max = 500, message = "A URL deve ter no máximo 500 caracteres.")
            @URL(message = "Informe uma URL válida para o canal oficial.") String urlCanalOficial,
            @Size(max = 20000, message = "O texto legal deve ter no máximo 20000 caracteres.") String textoLegal) {
    }
}
