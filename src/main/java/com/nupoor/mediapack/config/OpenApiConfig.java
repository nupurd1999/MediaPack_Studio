package com.nupoor.mediapack.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mediaPackStudioOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MediaPack Studio — Enterprise DAM & Packaging Engine API")
                        .description("High-performance Java 21 / Spring Boot 3 enterprise microservice engineered for multi-tenant digital asset management, asynchronous media packaging, and automated catalog syndication.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Nupoor Dhamal")
                                .email("developer@mediapack.studio")
                                .url("https://github.com/nupoordhamal"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")
                ));
    }

    /**
     * Adds the required X-Tenant-Region header documentation to all API endpoints.
     */
    @Bean
    public OpenApiCustomizer globalHeaderCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation ->
                        operation.addParametersItem(new HeaderParameter()
                                .name("X-Tenant-Region")
                                .description("Tenant Region Code for Dynamic Database Routing (e.g., DE, NL, US)")
                                .required(false)
                                .example("DE"))
                )
        );
    }
}
