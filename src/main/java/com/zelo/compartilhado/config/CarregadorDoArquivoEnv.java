package com.zelo.compartilhado.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lê o {@code .env} da pasta de execução (formato CHAVE=valor, UTF-8) como a fonte de propriedades de menor
 * prioridade: variáveis de ambiente reais e argumentos de linha de comando sempre prevalecem.
 * Não usamos {@code spring.config.import=file:.env[.properties]} porque arquivos .properties são lidos
 * em ISO-8859-1 e corromperiam acentos.
 */
public class CarregadorDoArquivoEnv implements EnvironmentPostProcessor {

    static final String NOME = "arquivo .env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path arquivo = Path.of(".env");
        if (!Files.isRegularFile(arquivo)) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource(NOME, ler(arquivo)));
    }

    static Map<String, Object> ler(Path arquivo) {
        Map<String, Object> valores = new LinkedHashMap<>();
        try {
            for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                String texto = linha.strip();
                if (texto.isEmpty() || texto.startsWith("#")) {
                    continue;
                }
                if (texto.startsWith("export ")) {
                    texto = texto.substring(7).strip();
                }
                int igual = texto.indexOf('=');
                if (igual <= 0) {
                    continue;
                }
                valores.put(texto.substring(0, igual).strip(), semAspas(texto.substring(igual + 1).strip()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo .env", e);
        }
        return valores;
    }

    private static String semAspas(String valor) {
        if (valor.length() >= 2 && ((valor.startsWith("\"") && valor.endsWith("\""))
                || (valor.startsWith("'") && valor.endsWith("'")))) {
            return valor.substring(1, valor.length() - 1);
        }
        return valor;
    }
}
