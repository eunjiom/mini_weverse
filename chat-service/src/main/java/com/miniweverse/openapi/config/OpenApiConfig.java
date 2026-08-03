package com.miniweverse.openapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chatServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini Weverse - Chat API")
                        .description("아티스트-팬 실시간 채팅(WebSocket/STOMP), 채팅 이력 조회를 담당하는 채팅 서비스")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
