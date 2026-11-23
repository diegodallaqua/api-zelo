package com.zelo.usuario.application;

import com.zelo.usuario.domain.ValidadorDeDominioDeEmail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UsuarioConfig {

    @Bean
    ValidadorDeDominioDeEmail validadorDeDominioDeEmail(UsuarioProperties properties) {
        return new ValidadorDeDominioDeEmail(properties.cadastro().dominiosEmailPermitidos());
    }
}
