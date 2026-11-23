package com.zelo.ocorrencia.infra.armazenamento;

import com.zelo.compartilhado.erro.ServicoIndisponivelException;
import com.zelo.ocorrencia.domain.ArmazenamentoDeImagens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Armazenamento em disco para desenvolvimento. Os arquivos são servidos em {@code /arquivos/**}. */
@Slf4j
@Component
@ConditionalOnProperty(name = "zelo.armazenamento.tipo", havingValue = "local", matchIfMissing = true)
public class ArmazenamentoLocal implements ArmazenamentoDeImagens, WebMvcConfigurer {

    private final Path raiz;
    private final String urlBase;

    public ArmazenamentoLocal(ArmazenamentoProperties properties) {
        this.raiz = Path.of(properties.local().diretorio()).toAbsolutePath().normalize();
        this.urlBase = properties.local().urlBase().replaceAll("/+$", "");
    }

    @Override
    public void salvar(String chave, byte[] conteudo, String contentType) {
        Path destino = resolver(chave);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, conteudo);
        } catch (IOException e) {
            throw new ServicoIndisponivelException("ARMAZENAMENTO_INDISPONIVEL",
                    "Não foi possível gravar a foto. Tente novamente.");
        }
    }

    @Override
    public void remover(String chave) {
        try {
            Files.deleteIfExists(resolver(chave));
        } catch (IOException e) {
            log.warn("Não foi possível remover o arquivo {}: {}", chave, e.getMessage());
        }
    }

    @Override
    public String urlPublica(String chave) {
        return urlBase + "/" + chave;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/arquivos/**")
                .addResourceLocations(raiz.toUri().toString().replaceAll("/?$", "/"))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }

    private Path resolver(String chave) {
        Path destino = raiz.resolve(chave).normalize();
        if (!destino.startsWith(raiz)) {
            throw new IllegalArgumentException("Chave de arquivo inválida: " + chave);
        }
        return destino;
    }
}
