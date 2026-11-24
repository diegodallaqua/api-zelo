package com.zelo.ocorrencia.infra.armazenamento;

import com.zelo.compartilhado.erro.ServicoIndisponivelException;
import com.zelo.ocorrencia.domain.ArmazenamentoDeImagens;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.stream.Stream;

/** Cloudflare R2 pela API compatível com S3. Ativado com {@code zelo.armazenamento.tipo=r2}. */
@Slf4j
@Component
@ConditionalOnProperty(name = "zelo.armazenamento.tipo", havingValue = "r2")
public class ArmazenamentoR2 implements ArmazenamentoDeImagens {

    private final S3Client s3;
    private final String bucket;
    private final String urlPublica;

    public ArmazenamentoR2(ArmazenamentoProperties properties) {
        ArmazenamentoProperties.R2 r2 = properties.r2();
        if (r2 == null || Stream.of(r2.accountId(), r2.accessKeyId(), r2.secretAccessKey(), r2.bucket(), r2.urlPublica())
                .anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalStateException("Armazenamento R2 selecionado, mas faltam variáveis: "
                    + "R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_BUCKET e R2_URL_PUBLICA.");
        }
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create("https://" + r2.accountId() + ".r2.cloudflarestorage.com"))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2.accessKeyId(), r2.secretAccessKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        this.bucket = r2.bucket();
        this.urlPublica = r2.urlPublica().replaceAll("/+$", "");
    }

    @Override
    public void salvar(String chave, byte[] conteudo, String contentType) {
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(chave)
                            .contentType(contentType)
                            .cacheControl("public, max-age=31536000, immutable")
                            .build(),
                    RequestBody.fromBytes(conteudo));
        } catch (SdkException e) {
            log.error("Falha ao enviar {} ao R2", chave, e);
            throw new ServicoIndisponivelException("ARMAZENAMENTO_INDISPONIVEL",
                    "Não foi possível gravar a foto. Tente novamente.");
        }
    }

    @Override
    public void remover(String chave) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(chave).build());
        } catch (SdkException e) {
            log.warn("Não foi possível remover {} do R2: {}", chave, e.getMessage());
        }
    }

    @Override
    public String urlPublica(String chave) {
        return urlPublica + "/" + chave;
    }

    @PreDestroy
    void fechar() {
        s3.close();
    }
}
