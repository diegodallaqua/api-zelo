package com.zelo.ocorrencia.infra;

import com.zelo.ocorrencia.domain.CalculadoraDePrioridade;
import com.zelo.ocorrencia.domain.PrioridadePorCurtidasEDiasEmAberto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OcorrenciaConfig {

    @Bean
    CalculadoraDePrioridade calculadoraDePrioridade() {
        return new PrioridadePorCurtidasEDiasEmAberto();
    }
}
