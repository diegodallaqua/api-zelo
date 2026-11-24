package com.zelo.localidade.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.localidade.domain.Cidade;
import com.zelo.localidade.domain.Estado;
import com.zelo.localidade.infra.CidadeRepository;
import com.zelo.localidade.infra.CidadeRepository.LimitesProjecao;
import com.zelo.localidade.infra.EstadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Serviço público do módulo de localidades (estados e cidades), usado pelos demais módulos. */
@Service
@RequiredArgsConstructor
public class CidadeService {

    private final CidadeRepository cidades;
    private final EstadoRepository estados;
    private final LocalidadeProperties properties;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public record CidadeResumo(Integer id, String nome, Integer estadoId, String estado) {

        public static CidadeResumo de(Cidade c) {
            return new CidadeResumo(c.getId(), c.getNome(), c.getEstado().getId(), c.getEstado().getNome());
        }
    }

    public record Limites(double minLongitude, double minLatitude, double maxLongitude, double maxLatitude,
                          double latitudeCentral, double longitudeCentral) {
    }

    @Transactional(readOnly = true)
    public List<Estado> listarEstados() {
        return estados.findAllByOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public Cidade obter(Integer id) {
        return cidades.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CIDADE_NAO_ENCONTRADA",
                        "Cidade não encontrada: " + id + "."));
    }

    @Transactional(readOnly = true)
    public Limites limites(Integer id) {
        LimitesProjecao p = cidades.buscarLimites(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CIDADE_NAO_ENCONTRADA",
                        "Cidade não encontrada: " + id + "."));
        return new Limites(p.getMinLongitude(), p.getMinLatitude(), p.getMaxLongitude(), p.getMaxLatitude(),
                p.getLatitudeCentral(), p.getLongitudeCentral());
    }

    @Transactional(readOnly = true)
    public Page<Cidade> pesquisar(String nome, Integer estadoId, Pageable pageable) {
        String termo = nome == null || nome.isBlank() ? null : nome.trim().replace("%", "").replace("_", "");
        return cidades.pesquisar(termo, estadoId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Cidade> localizar(double latitude, double longitude) {
        return cidades.buscarIdContendoPonto(latitude, longitude).flatMap(cidades::findById);
    }

    /**
     * Regra 3.2 / RF09: o ponto da ocorrência e a localização do dispositivo devem cair na mesma
     * cidade cadastrada e ativa. Retorna o código IBGE dessa cidade.
     */
    @Transactional(readOnly = true)
    public Integer validarLocalDaOcorrencia(double latitude, double longitude,
                                            double latitudeDispositivo, double longitudeDispositivo) {
        Integer doPonto = cidades.buscarIdContendoPonto(latitude, longitude)
                .orElseThrow(() -> new RegraDeNegocioException("LOCAL_FORA_DE_CIDADE",
                        "O ponto marcado não está dentro de nenhuma cidade cadastrada."));
        Integer doDispositivo = cidades.buscarIdContendoPonto(latitudeDispositivo, longitudeDispositivo)
                .orElseThrow(() -> new RegraDeNegocioException("DISPOSITIVO_FORA_DE_CIDADE",
                        "Sua localização atual não está dentro de nenhuma cidade cadastrada."));
        if (!doPonto.equals(doDispositivo)) {
            throw new RegraDeNegocioException("CIDADE_DIFERENTE_DO_DISPOSITIVO",
                    "A ocorrência deve estar na mesma cidade em que você se encontra.");
        }
        Cidade cidade = obter(doPonto);
        if (!cidade.isAtivo()) {
            throw new RegraDeNegocioException("CIDADE_INATIVA",
                    "O Zelo ainda não está disponível em " + cidade.getNome() + ".");
        }
        return doPonto;
    }

    /** Valida uma cidade informada em dados de entrada (ex.: endereço). */
    @Transactional(readOnly = true)
    public void exigirExistente(Integer id) {
        if (id == null || !cidades.existsById(id)) {
            throw new RegraDeNegocioException("CIDADE_INVALIDA", "Cidade não encontrada: " + id + ".");
        }
    }

    @Transactional(readOnly = true)
    public Map<Integer, CidadeResumo> resumos(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return cidades.findAllById(ids).stream()
                .map(CidadeResumo::de)
                .collect(Collectors.toMap(CidadeResumo::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public CidadeResumo resumo(Integer id) {
        return CidadeResumo.de(obter(id));
    }

    public String avisoCanalNaoOficial(Cidade cidade) {
        return cidade.getAvisoCanalNaoOficial() != null
                ? cidade.getAvisoCanalNaoOficial()
                : properties.avisoCanalNaoOficialPadrao();
    }

    @Transactional
    public Cidade configurar(Integer id, boolean ativo, String aviso, String urlCanalOficial, String textoLegal,
                             Ator ator) {
        Cidade cidade = obter(id);
        cidade.configurar(ativo, vazioParaNulo(aviso), vazioParaNulo(urlCanalOficial), vazioParaNulo(textoLegal),
                clock.instant());
        auditoria.registrar(ator, "CIDADE_CONFIGURADA", "CIDADE", id, Map.of("ativo", ativo));
        return cidade;
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
