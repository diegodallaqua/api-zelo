package com.zelo.ocorrencia.application;

import com.zelo.categoria.application.CategoriaService;
import com.zelo.categoria.application.CategoriaService.CategoriaResumo;
import com.zelo.localidade.application.CidadeService;
import com.zelo.ocorrencia.application.Visoes.FotoVisao;
import com.zelo.ocorrencia.application.Visoes.HistoricoVisao;
import com.zelo.ocorrencia.application.Visoes.OcorrenciaDetalhe;
import com.zelo.ocorrencia.application.Visoes.OcorrenciaResumo;
import com.zelo.ocorrencia.domain.ArmazenamentoDeImagens;
import com.zelo.ocorrencia.domain.FotoOcorrencia;
import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.infra.ComentarioRepository;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia;
import com.zelo.usuario.application.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Converte o agregado em modelos de leitura, buscando nomes de categoria, cidade e autor em lote. */
@Component
@RequiredArgsConstructor
class MontadorDeVisoes {

    static final String AUTOR_ANONIMO = "Anônimo";
    static final String AUTOR_REMOVIDO = "Usuário removido";

    private final CategoriaService categoriaService;
    private final CidadeService cidadeService;
    private final UsuarioService usuarioService;
    private final ComentarioRepository comentarios;
    private final ConsultasDeOcorrencia consultas;
    private final ArmazenamentoDeImagens armazenamento;
    private final OcorrenciaProperties properties;

    /** Deve rodar dentro de transação (coleções lazy). */
    OcorrenciaDetalhe detalhe(Ocorrencia o, UUID usuarioId) {
        CategoriaResumo categoria = categoriaService.resumos(Set.of(o.getCategoriaId())).get(o.getCategoriaId());
        String autor;
        if (o.isAnonima()) {
            autor = AUTOR_ANONIMO;
        } else if (o.getAutorId() == null) {
            autor = AUTOR_REMOVIDO;
        } else {
            autor = usuarioService.nomesPublicos(Set.of(o.getAutorId())).getOrDefault(o.getAutorId(), AUTOR_REMOVIDO);
        }
        Boolean curtidaPorMim = usuarioId == null ? null : consultas.curtiu(o.getId(), usuarioId);
        return new OcorrenciaDetalhe(o.getId(), o.getStatus(), categoria, cidadeService.resumo(o.getCidadeId()),
                o.getDescricao(), o.getLatitude(), o.getLongitude(), o.isAnonima(), autor, o.ehAutor(usuarioId),
                o.getFotos().stream().map(this::foto).toList(), o.getTotalCurtidas(), curtidaPorMim,
                comentarios.countByOcorrenciaIdAndRemovidoEmIsNull(o.getId()),
                o.getHistorico().stream().map(HistoricoVisao::de).toList(), o.getOcorrenciaPrincipalId(),
                o.getCriadaEm(), o.getAtualizadaEm(), o.getResolvidaEm(), o.getResolucaoConfirmadaEm(),
                o.prazoDeContestacao(properties.prazoContestacao()).orElse(null));
    }

    /** Deve rodar dentro de transação (coleções lazy). */
    List<OcorrenciaResumo> resumos(Collection<Ocorrencia> ocorrencias) {
        Map<UUID, CategoriaResumo> categorias = categoriaService.resumos(
                ocorrencias.stream().map(Ocorrencia::getCategoriaId).collect(Collectors.toSet()));
        return ocorrencias.stream().map(o -> new OcorrenciaResumo(o.getId(), o.getStatus(),
                        categorias.get(o.getCategoriaId()), o.getCidadeId(), o.getDescricao(), o.getLatitude(),
                        o.getLongitude(), o.getTotalCurtidas(), o.getPrioridade(),
                        o.getFotos().isEmpty() ? null : armazenamento.urlPublica(o.getFotos().get(0).getChave()),
                        o.getCriadaEm(), o.getUltimaAtividadeEm()))
                .toList();
    }

    FotoVisao foto(FotoOcorrencia f) {
        return new FotoVisao(f.getId(), armazenamento.urlPublica(f.getChave()), f.getLargura(), f.getAltura());
    }
}
