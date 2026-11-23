package com.zelo.ocorrencia.api;

import com.zelo.ocorrencia.domain.Comentario;
import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class OcorrenciaDtos {

    private OcorrenciaDtos() {
    }

    /** Campos do multipart de criação; as fotos vão no campo {@code fotos} (um ou mais arquivos). */
    public record NovaOcorrenciaForm(
            @NotNull(message = "A categoria é obrigatória.") UUID categoriaId,
            @NotBlank(message = "A descrição é obrigatória.")
            @Size(min = Ocorrencia.DESCRICAO_MINIMO, max = Ocorrencia.DESCRICAO_MAXIMO,
                    message = "A descrição deve ter entre {min} e {max} caracteres.") String descricao,
            @NotNull(message = "A latitude do problema é obrigatória.")
            @DecimalMin(value = "-90", message = "Latitude deve estar entre -90 e 90.")
            @DecimalMax(value = "90", message = "Latitude deve estar entre -90 e 90.") Double latitude,
            @NotNull(message = "A longitude do problema é obrigatória.")
            @DecimalMin(value = "-180", message = "Longitude deve estar entre -180 e 180.")
            @DecimalMax(value = "180", message = "Longitude deve estar entre -180 e 180.") Double longitude,
            @NotNull(message = "A latitude do dispositivo é obrigatória.")
            @DecimalMin(value = "-90", message = "Latitude deve estar entre -90 e 90.")
            @DecimalMax(value = "90", message = "Latitude deve estar entre -90 e 90.") Double latitudeDispositivo,
            @NotNull(message = "A longitude do dispositivo é obrigatória.")
            @DecimalMin(value = "-180", message = "Longitude deve estar entre -180 e 180.")
            @DecimalMax(value = "180", message = "Longitude deve estar entre -180 e 180.") Double longitudeDispositivo,
            Boolean anonima,
            Boolean ignorarSemelhantes) {
    }

    public record EditarOcorrenciaRequest(
            @Size(min = Ocorrencia.DESCRICAO_MINIMO, max = Ocorrencia.DESCRICAO_MAXIMO,
                    message = "A descrição deve ter entre {min} e {max} caracteres.") String descricao,
            UUID categoriaId) {
    }

    public record TransicaoRequest(
            @NotNull(message = "O novo status é obrigatório.") StatusOcorrencia status,
            @Size(max = 2000, message = "O motivo deve ter no máximo 2000 caracteres.") String motivo) {
    }

    public record ContestacaoRequest(
            @NotBlank(message = "Informe o motivo da contestação.")
            @Size(max = 2000, message = "O motivo deve ter no máximo 2000 caracteres.") String motivo) {
    }

    public record ComentarioRequest(
            @NotBlank(message = "O comentário não pode ser vazio.")
            @Size(max = Comentario.TEXTO_MAXIMO, message = "O comentário deve ter no máximo {max} caracteres.") String texto) {
    }
}
