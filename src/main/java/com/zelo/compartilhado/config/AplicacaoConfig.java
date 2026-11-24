package com.zelo.compartilhado.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AplicacaoConfig {

    /** Relógio injetável: as regras de prazo usam {@code clock.instant()} e ficam testáveis. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Zelo API")
                        .version("v1")
                        .description("""
                                API do Zelo: registro colaborativo de problemas da infraestrutura urbana.
                                O Zelo não é um canal oficial do poder público.
                                Erros seguem a RFC 9457 (application/problem+json) com o campo `codigo`."""))
                .components(new Components().addSecuritySchemes("bearer", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
