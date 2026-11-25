package com.clinic.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI clinicApi() {
        String schemeName = "firebaseBearer";
        return new OpenAPI()
                .info(new Info().title("Clinic Management API").version("v1"))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("Firebase ID token")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}
