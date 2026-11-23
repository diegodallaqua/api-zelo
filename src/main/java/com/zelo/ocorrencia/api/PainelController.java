package com.zelo.ocorrencia.api;

import com.zelo.compartilhado.api.PaginaResposta;
import com.zelo.compartilhado.api.Paginacao;
import com.zelo.compartilhado.seguranca.UsuarioAutenticado;
import com.zelo.ocorrencia.application.ConsultaDeOcorrenciaService;
import com.zelo.ocorrencia.application.FiltroDeOcorrencias;
import com.zelo.ocorrencia.application.FiltroDeOcorrencias.Ordenacao;
import com.zelo.ocorrencia.application.GestaoDeOcorrenciasService;
import com.zelo.ocorrencia.application.Visoes.EstatisticasVisao;
import com.zelo.ocorrencia.application.Visoes.OcorrenciaResumo;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Listagens: ocorrências com filtros, minhas ocorrências, painel do gestor e estatísticas públicas. */
@RestController
@RequiredArgsConstructor
public class PainelController {

    private final ConsultaDeOcorrenciaService consultas;
    private final GestaoDeOcorrenciasService gestao;

    @Tag(name = "Minha conta")
    @Operation(summary = "Ocorrências registradas pelo usuário autenticado")
    @GetMapping("/api/v1/me/ocorrencias")
    public PaginaResposta<OcorrenciaResumo> minhas(@RequestParam(defaultValue = "0") int pagina,
                                                   @RequestParam(defaultValue = "20") int tamanho,
                                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PaginaResposta.de(consultas.doAutor(usuario.id(), Paginacao.de(pagina, tamanho)));
    }

    @Tag(name = "Ocorrências")
    @Operation(summary = "Lista ocorrências com filtros de cidade, categoria, status e período (RF06)",
            description = "Sem filtro de status, retorna ABERTA, EM_ANDAMENTO e RESOLVIDA. Datas no formato AAAA-MM-DD.")
    @GetMapping("/api/v1/ocorrencias")
    public PaginaResposta<OcorrenciaResumo> listar(
            @RequestParam(required = false) Integer cidadeId,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(required = false) Set<StatusOcorrencia> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "MAIS_RECENTES") Ordenacao ordenacao,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return PaginaResposta.de(consultas.listar(new FiltroDeOcorrencias(cidadeId, categoriaId, status, de, ate, ordenacao),
                StatusOcorrencia.VISIVEIS_NO_MAPA_POR_PADRAO, Ordenacao.MAIS_RECENTES, Paginacao.de(pagina, tamanho)));
    }

    @Tag(name = "Gestão")
    @Operation(summary = "Painel do gestor: ocorrências da área de atuação",
            description = "Gestor e moderador veem a cidade do próprio endereço; admin informa cidadeId. Filtre por categoria com categoriaId.")
    @GetMapping("/api/v1/gestao/ocorrencias")
    public PaginaResposta<OcorrenciaResumo> painel(
            @RequestParam(required = false) Integer cidadeId,
            @RequestParam(required = false) Set<StatusOcorrencia> status,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "PRIORIDADE") Ordenacao ordenacao,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PaginaResposta.de(gestao.painel(usuario.id(),
                new FiltroDeOcorrencias(cidadeId, categoriaId, status, de, ate, ordenacao), Paginacao.de(pagina, tamanho)));
    }

    @Tag(name = "Transparência")
    @Operation(summary = "Estatísticas públicas da cidade: total, abertas, resolvidas e tempo médio por categoria")
    @GetMapping("/api/v1/estatisticas")
    public EstatisticasVisao estatisticas(
            @RequestParam Integer cidadeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return consultas.estatisticas(cidadeId, de, ate);
    }
}
