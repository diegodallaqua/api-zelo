package com.zelo.ocorrencia.application;

import com.zelo.categoria.application.CategoriaService;
import com.zelo.categoria.application.CategoriaService.CategoriaResumo;
import com.zelo.compartilhado.erro.RequisicaoInvalidaException;
import com.zelo.localidade.application.CidadeService;
import com.zelo.ocorrencia.application.Visoes.EstatisticaVisao;
import com.zelo.ocorrencia.application.Visoes.EstatisticasVisao;
import com.zelo.ocorrencia.application.Visoes.MapaVisao;
import com.zelo.ocorrencia.application.Visoes.MarcadorVisao;
import com.zelo.ocorrencia.application.Visoes.OcorrenciaDetalhe;
import com.zelo.ocorrencia.application.Visoes.OcorrenciaResumo;
import com.zelo.ocorrencia.domain.DetectorDeDuplicidade;
import com.zelo.ocorrencia.domain.DetectorDeDuplicidade.Semelhante;
import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia.EstatisticaLinha;
import com.zelo.ocorrencia.infra.ConsultasDeOcorrencia.MarcadorDoMapa;
import com.zelo.ocorrencia.infra.OcorrenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Leituras públicas: mapa (RF03, RF06), detalhe (RF13), semelhantes (RF10) e estatísticas (RF22). */
@Service
@RequiredArgsConstructor
public class ConsultaDeOcorrenciaService {

    private final OcorrenciaRepository repository;
    private final ConsultasDeOcorrencia consultas;
    private final DetectorDeDuplicidade detectorDeDuplicidade;
    private final CategoriaService categoriaService;
    private final CidadeService cidadeService;
    private final MontadorDeVisoes visoes;
    private final OcorrenciaProperties properties;

    public record FiltroDoMapa(double minLongitude, double minLatitude, double maxLongitude, double maxLatitude,
                               Set<StatusOcorrencia> status, Set<UUID> categorias, LocalDate de, LocalDate ate,
                               Integer limite) {
    }

    /**
     * Listagem paginada com filtros de cidade, categoria, status e período (RF06).
     *
     * @param statusPadrao status usados quando o filtro não informa nenhum; nulo = todos
     */
    @Transactional(readOnly = true)
    public Page<OcorrenciaResumo> listar(FiltroDeOcorrencias filtro, Set<StatusOcorrencia> statusPadrao,
                                         FiltroDeOcorrencias.Ordenacao ordenacaoPadrao, Pageable pagina) {
        Instant[] periodo = periodo(filtro.de(), filtro.ate());
        Page<Ocorrencia> resultado = repository.findAll(filtro.especificacao(statusPadrao, periodo),
                PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(),
                        filtro.ordenacaoOuPadrao(ordenacaoPadrao).sort()));
        return new PageImpl<>(visoes.resumos(resultado.getContent()), resultado.getPageable(),
                resultado.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OcorrenciaDetalhe detalhar(UUID id, UUID usuarioId) {
        Ocorrencia o = repository.findById(id).orElseThrow(RegistroDeOcorrenciaService::naoEncontrada);
        o.exigirNaoRemovida();
        return visoes.detalhe(o, usuarioId);
    }

    @Transactional(readOnly = true)
    public MapaVisao mapa(FiltroDoMapa filtro) {
        if (filtro.minLongitude() >= filtro.maxLongitude() || filtro.minLatitude() >= filtro.maxLatitude()) {
            throw new RequisicaoInvalidaException("AREA_INVALIDA",
                    "A área do mapa é inválida: o mínimo deve ser menor que o máximo em latitude e longitude.");
        }
        OcorrenciaProperties.Mapa config = properties.mapa();
        int limite = filtro.limite() == null ? config.limitePadrao() : filtro.limite();
        if (limite < 1 || limite > config.limiteMaximo()) {
            throw new RequisicaoInvalidaException("LIMITE_INVALIDO",
                    "O parâmetro 'limite' deve estar entre 1 e " + config.limiteMaximo() + ".");
        }
        Collection<StatusOcorrencia> status = filtro.status() == null || filtro.status().isEmpty()
                ? StatusOcorrencia.VISIVEIS_NO_MAPA_POR_PADRAO
                : filtro.status();
        Instant[] periodo = periodo(filtro.de(), filtro.ate());
        List<MarcadorDoMapa> marcadores = consultas.marcadoresNaArea(filtro.minLongitude(), filtro.minLatitude(),
                filtro.maxLongitude(), filtro.maxLatitude(), status, filtro.categorias(), periodo[0], periodo[1], limite);
        boolean truncado = marcadores.size() > limite;
        List<MarcadorVisao> itens = marcadores.stream().limit(limite)
                .map(m -> new MarcadorVisao(m.id(), m.latitude(), m.longitude(), m.status(), m.categoriaId(),
                        m.totalCurtidas()))
                .toList();
        return new MapaVisao(itens, limite, truncado);
    }

    @Transactional(readOnly = true)
    public List<Semelhante> semelhantes(double latitude, double longitude, UUID categoriaId) {
        return detectorDeDuplicidade.buscar(latitude, longitude, categoriaId);
    }

    @Transactional(readOnly = true)
    public Page<OcorrenciaResumo> doAutor(UUID usuarioId, Pageable pageable) {
        Page<Ocorrencia> pagina = repository.findByAutorIdAndRemovidaEmIsNullOrderByCriadaEmDesc(usuarioId, pageable);
        return new PageImpl<>(visoes.resumos(pagina.getContent()), pageable, pagina.getTotalElements());
    }

    @Transactional(readOnly = true)
    public EstatisticasVisao estatisticas(Integer cidadeId, LocalDate de, LocalDate ate) {
        CidadeService.CidadeResumo cidade = cidadeService.resumo(cidadeId);
        Instant[] periodo = periodo(de, ate);
        List<EstatisticaLinha> linhas = consultas.estatisticas(cidadeId, periodo[0], periodo[1]);
        Map<UUID, CategoriaResumo> categorias = categoriaService.resumos(linhas.stream()
                .map(EstatisticaLinha::categoriaId).filter(id -> id != null).collect(Collectors.toSet()));
        EstatisticaVisao geral = linhas.stream().filter(l -> l.categoriaId() == null).findFirst()
                .map(l -> visao(l, null))
                .orElse(new EstatisticaVisao(null, 0, 0, 0, 0, 0, null));
        List<EstatisticaVisao> porCategoria = linhas.stream().filter(l -> l.categoriaId() != null)
                .map(l -> visao(l, categorias.get(l.categoriaId())))
                .toList();
        return new EstatisticasVisao(cidade, geral, porCategoria);
    }

    /** Autor e apoiadores (curtidas): quem é avisado das mudanças de status (RF17). */
    @Transactional(readOnly = true)
    public Set<UUID> interessados(UUID ocorrenciaId) {
        return consultas.interessados(ocorrenciaId);
    }

    /** Novas ocorrências na cidade desde o instante (resumo do gestor). */
    @Transactional(readOnly = true)
    public long contarNovas(Integer cidadeId, Instant desde) {
        return repository.contarNovas(cidadeId, desde);
    }

    /** Converte datas civis (inclusivas) no intervalo [início de "de", início do dia seguinte a "ate"). */
    Instant[] periodo(LocalDate de, LocalDate ate) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new RequisicaoInvalidaException("PERIODO_INVALIDO", "A data inicial deve ser anterior ou igual à final.");
        }
        ZoneId fuso = properties.fusoHorario();
        return new Instant[]{
                de == null ? null : de.atStartOfDay(fuso).toInstant(),
                ate == null ? null : ate.plusDays(1).atStartOfDay(fuso).toInstant()
        };
    }

    private static EstatisticaVisao visao(EstatisticaLinha l, CategoriaResumo categoria) {
        Double horas = l.tempoMedioResolucaoHoras() == null ? null : Math.round(l.tempoMedioResolucaoHoras() * 10) / 10.0;
        return new EstatisticaVisao(categoria, l.total(), l.abertas(), l.emAndamento(), l.resolvidas(), l.arquivadas(),
                horas);
    }
}
