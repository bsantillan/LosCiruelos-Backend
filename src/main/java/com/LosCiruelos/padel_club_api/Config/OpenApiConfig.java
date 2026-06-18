package com.LosCiruelos.padel_club_api.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pasarLaPaginaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Los Ciruelos Padel Club API")
                        .description("Documentacion de los endpoints REST de Los Ciruelos Padel Club.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipo Backend")
                                .email("backend@losciruelos.local")));
    }
}
