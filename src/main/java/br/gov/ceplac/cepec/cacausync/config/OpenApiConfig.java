package br.gov.ceplac.cepec.cacausync.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("CacauSync API — Gestão de Ensaios Genéticos e Sincronização de Campo (CEPEC/CEPLAC)")
                        .version("1.0.0")
                        .description("Back-end central em Java 21 + Spring Boot 3 para planejamento de rotas FieldBook, " +
                                "importação de planilhas CSV exportadas pelo aplicativo Android CacauApp (TFLite Offline) " +
                                "e relatórios consolidados de produtividade e sobrevivência por clone.")
                        .contact(new Contact()
                                .name("Programa de Melhoramento Genético do Cacaueiro — CEPEC/CEPLAC")
                                .url("https://github.com/Igor8818/CacauSync")))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Insira o token JWT obtido no endpoint POST /api/v1/auth/login")));
    }
}
