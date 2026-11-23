package com.zelo.ocorrencia.application;

import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.RequisicaoInvalidaException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.ocorrencia.application.FiltroDeOcorrencias.Ordenacao;
import com.zelo.ocorrencia.application.Visoes.OcorrenciaResumo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Painel do gestor (RF20): ocorrências da cidade em que ele atua (a do seu endereço), com todos os status
 * por padrão e ordenação por prioridade. O filtro por categoria é feito pelo parâmetro {@code categoriaId}.
 */
@Service
@RequiredArgsConstructor
public class GestaoDeOcorrenciasService {

    private final ProvedorDeAtor provedorDeAtor;
    private final ConsultaDeOcorrenciaService consultas;

    @Transactional(readOnly = true)
    public Page<OcorrenciaResumo> painel(UUID usuarioId, FiltroDeOcorrencias filtro, Pageable pagina) {
        Ator ator = provedorDeAtor.obter(usuarioId);
        FiltroDeOcorrencias daArea = filtro.comCidade(cidadeDoPainel(ator, filtro.cidadeId()));
        return consultas.listar(daArea, null, Ordenacao.PRIORIDADE, pagina);
    }

    private static Integer cidadeDoPainel(Ator ator, Integer solicitada) {
        if (ator.isAdmin()) {
            if (solicitada == null) {
                throw new RequisicaoInvalidaException("CIDADE_OBRIGATORIA",
                        "Administradores devem informar o parâmetro 'cidadeId'.");
            }
            return solicitada;
        }
        if (ator.cidadeId() == null) {
            throw new AcessoNegadoException("SEM_AREA_DE_ATUACAO", "Seu usuário não tem cidade de atuação definida.");
        }
        if (solicitada != null && !solicitada.equals(ator.cidadeId())) {
            throw new AcessoNegadoException("FORA_DA_AREA_DE_ATUACAO", "Você só pode consultar a sua cidade.");
        }
        return ator.cidadeId();
    }
}
