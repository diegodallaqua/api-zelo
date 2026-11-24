package com.zelo.compartilhado.auditoria;

import com.zelo.compartilhado.api.PaginaResposta;
import com.zelo.compartilhado.api.Paginacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Admin: auditoria")
@RestController
@RequestMapping("/api/v1/admin/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService service;

    public record RegistroAuditoriaResposta(Long id, UUID atorId, String papelAtor, String acao, String entidade,
                                            String entidadeId, String detalhes, Instant ocorridoEm) {
    }

    @Operation(summary = "Consulta a trilha de auditoria das ações sensíveis")
    @GetMapping
    public PaginaResposta<RegistroAuditoriaResposta> pesquisar(
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) UUID atorId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return PaginaResposta.de(service.pesquisar(entidade, entidadeId, atorId, Paginacao.de(pagina, tamanho)),
                r -> new RegistroAuditoriaResposta(r.getId(), r.getAtorId(), r.getPapelAtor(), r.getAcao(),
                        r.getEntidade(), r.getEntidadeId(), r.getDetalhes(), r.getOcorridoEm()));
    }
}
