package com.zelo.ocorrencia.api;

import com.zelo.compartilhado.api.PaginaResposta;
import com.zelo.compartilhado.api.Paginacao;
import com.zelo.compartilhado.seguranca.UsuarioAutenticado;
import com.zelo.ocorrencia.api.OcorrenciaDtos.ComentarioRequest;
import com.zelo.ocorrencia.api.OcorrenciaDtos.ContestacaoRequest;
import com.zelo.ocorrencia.api.OcorrenciaDtos.EditarOcorrenciaRequest;
import com.zelo.ocorrencia.api.OcorrenciaDtos.NovaOcorrenciaForm;
import com.zelo.ocorrencia.api.OcorrenciaDtos.TransicaoRequest;
import com.zelo.ocorrencia.application.CicloDeVidaService;
import com.zelo.ocorrencia.application.ComentarioService;
import com.zelo.ocorrencia.application.ConsultaDeOcorrenciaService;
import com.zelo.ocorrencia.application.ConsultaDeOcorrenciaService.FiltroDoMapa;
import com.zelo.ocorrencia.application.CurtidaService;
import com.zelo.ocorrencia.application.RegistroDeOcorrenciaService;
import com.zelo.ocorrencia.application.RegistroDeOcorrenciaService.NovaOcorrencia;
import com.zelo.ocorrencia.application.Visoes.ComentarioVisao;
import com.zelo.ocorrencia.application.Visoes.CurtidaVisao;
import com.zelo.ocorrencia.application.Visoes.FotoVisao;
import com.zelo.ocorrencia.application.Visoes.MapaVisao;
import com.zelo.ocorrencia.application.Visoes.OcorrenciaDetalhe;
import com.zelo.ocorrencia.domain.DetectorDeDuplicidade.Semelhante;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Ocorrências")
@RestController
@RequestMapping("/api/v1/ocorrencias")
@RequiredArgsConstructor
public class OcorrenciaController {

    private final ConsultaDeOcorrenciaService consultas;
    private final RegistroDeOcorrenciaService registro;
    private final CicloDeVidaService cicloDeVida;
    private final CurtidaService curtidas;
    private final ComentarioService comentarios;

    // ------------------------------------------------------------------ leitura pública

    @Operation(summary = "Marcadores da área visível do mapa (resposta enxuta; a clusterização é feita no app)",
            description = "Sem filtro de status, retorna ABERTA, EM_ANDAMENTO e RESOLVIDA. Datas no formato AAAA-MM-DD.")
    @GetMapping("/mapa")
    public MapaVisao mapa(
            @RequestParam @DecimalMin(value = "-180", message = "minLongitude deve estar entre -180 e 180.")
            @DecimalMax(value = "180", message = "minLongitude deve estar entre -180 e 180.") double minLongitude,
            @RequestParam @DecimalMin(value = "-90", message = "minLatitude deve estar entre -90 e 90.")
            @DecimalMax(value = "90", message = "minLatitude deve estar entre -90 e 90.") double minLatitude,
            @RequestParam @DecimalMin(value = "-180", message = "maxLongitude deve estar entre -180 e 180.")
            @DecimalMax(value = "180", message = "maxLongitude deve estar entre -180 e 180.") double maxLongitude,
            @RequestParam @DecimalMin(value = "-90", message = "maxLatitude deve estar entre -90 e 90.")
            @DecimalMax(value = "90", message = "maxLatitude deve estar entre -90 e 90.") double maxLatitude,
            @RequestParam(required = false) Set<StatusOcorrencia> status,
            @RequestParam(name = "categoriaId", required = false) Set<UUID> categorias,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Integer limite) {
        return consultas.mapa(new FiltroDoMapa(minLongitude, minLatitude, maxLongitude, maxLatitude, status, categorias,
                de, ate, limite));
    }

    @Operation(summary = "Ocorrências ativas semelhantes perto do ponto (consultar antes de criar)")
    @GetMapping("/semelhantes")
    public List<Semelhante> semelhantes(
            @RequestParam @DecimalMin(value = "-90", message = "Latitude deve estar entre -90 e 90.")
            @DecimalMax(value = "90", message = "Latitude deve estar entre -90 e 90.") double latitude,
            @RequestParam @DecimalMin(value = "-180", message = "Longitude deve estar entre -180 e 180.")
            @DecimalMax(value = "180", message = "Longitude deve estar entre -180 e 180.") double longitude,
            @RequestParam UUID categoriaId) {
        return consultas.semelhantes(latitude, longitude, categoriaId);
    }

    @Operation(summary = "Detalhes: fotos, status, histórico, curtidas e total de comentários")
    @GetMapping("/{id}")
    public OcorrenciaDetalhe detalhar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return consultas.detalhar(id, idDe(usuario));
    }

    @Operation(summary = "Comentários da ocorrência, do mais antigo ao mais recente")
    @GetMapping("/{id}/comentarios")
    public PaginaResposta<ComentarioVisao> comentarios(@PathVariable UUID id,
                                                       @RequestParam(defaultValue = "0") int pagina,
                                                       @RequestParam(defaultValue = "20") int tamanho,
                                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PaginaResposta.de(comentarios.listar(id, idDe(usuario), Paginacao.de(pagina, tamanho)));
    }

    // ------------------------------------------------------------------ autor

    @Operation(summary = "Registra ocorrência (multipart com campos + fotos)",
            description = "Responde 409 com a lista 'semelhantes' quando há ocorrência parecida a até 25 m; "
                    + "reenvie com ignorarSemelhantes=true para criar mesmo assim.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcorrenciaDetalhe> registrar(@Valid @ModelAttribute NovaOcorrenciaForm form,
                                                       @RequestParam(name = "fotos", required = false) List<MultipartFile> fotos,
                                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        UUID id = registro.registrar(new NovaOcorrencia(form.categoriaId(), form.descricao(), form.latitude(),
                form.longitude(), form.latitudeDispositivo(), form.longitudeDispositivo(),
                Boolean.TRUE.equals(form.anonima()), Boolean.TRUE.equals(form.ignorarSemelhantes())), fotos, usuario.id());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri())
                .body(consultas.detalhar(id, usuario.id()));
    }

    @Operation(summary = "Edita descrição e/ou categoria (autor, enquanto em análise ou aberta)")
    @PatchMapping("/{id}")
    public OcorrenciaDetalhe editar(@PathVariable UUID id, @Valid @RequestBody EditarOcorrenciaRequest req,
                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        registro.editar(id, usuario.id(), req.descricao(), req.categoriaId());
        return consultas.detalhar(id, usuario.id());
    }

    @Operation(summary = "Exclui a própria ocorrência (autor, enquanto em análise ou aberta)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        registro.excluir(id, usuario.id());
    }

    @Operation(summary = "Adiciona uma foto (campo multipart 'foto')")
    @PostMapping(path = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FotoVisao adicionarFoto(@PathVariable UUID id, @RequestParam("foto") MultipartFile foto,
                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return registro.adicionarFoto(id, foto, usuario.id());
    }

    @Operation(summary = "Remove uma foto (mantém ao menos o mínimo exigido)")
    @DeleteMapping("/{id}/fotos/{fotoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerFoto(@PathVariable UUID id, @PathVariable UUID fotoId,
                            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        registro.removerFoto(id, fotoId, usuario.id());
    }

    @Operation(summary = "Autor confirma a resolução (encerra a ocorrência)")
    @PostMapping("/{id}/resolucao/confirmacao")
    public OcorrenciaDetalhe confirmar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        cicloDeVida.confirmarResolucao(id, usuario.id());
        return consultas.detalhar(id, usuario.id());
    }

    @Operation(summary = "Autor contesta a resolução no prazo; a ocorrência é reaberta")
    @PostMapping("/{id}/resolucao/contestacao")
    public OcorrenciaDetalhe contestar(@PathVariable UUID id, @Valid @RequestBody ContestacaoRequest req,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        cicloDeVida.contestarResolucao(id, req.motivo(), usuario.id());
        return consultas.detalhar(id, usuario.id());
    }

    // ------------------------------------------------------------------ participação

    @Operation(summary = "\"Também vejo esse problema\": curte a ocorrência (uma vez por usuário)")
    @PostMapping("/{id}/curtida")
    @ResponseStatus(HttpStatus.CREATED)
    public CurtidaVisao curtir(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return curtidas.curtir(id, usuario.id());
    }

    @Operation(summary = "Remove a curtida")
    @DeleteMapping("/{id}/curtida")
    public CurtidaVisao descurtir(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return curtidas.descurtir(id, usuario.id());
    }

    @Operation(summary = "Comenta na ocorrência")
    @PostMapping("/{id}/comentarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ComentarioVisao comentar(@PathVariable UUID id, @Valid @RequestBody ComentarioRequest req,
                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return comentarios.criar(id, usuario.id(), req.texto());
    }

    // ------------------------------------------------------------------ equipe

    @Operation(summary = "Altera o status (moderador, gestor ou admin, conforme a matriz de transições)",
            description = "Rejeição, interrupção de atendimento e reabertura exigem motivo.")
    @PostMapping("/{id}/transicoes")
    public OcorrenciaDetalhe transicionar(@PathVariable UUID id, @Valid @RequestBody TransicaoRequest req,
                                          @AuthenticationPrincipal UsuarioAutenticado usuario) {
        cicloDeVida.transicionar(id, req.status(), req.motivo(), usuario.id());
        return consultas.detalhar(id, usuario.id());
    }

    private static UUID idDe(UsuarioAutenticado usuario) {
        return usuario == null ? null : usuario.id();
    }
}
