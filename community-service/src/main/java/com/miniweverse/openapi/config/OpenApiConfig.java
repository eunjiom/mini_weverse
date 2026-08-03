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
    public OpenAPI communityServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini Weverse - Community API")
                        .description("회원가입/로그인, 아티스트 팔로우, 게시글/댓글, 유료 멤버십 구독을 담당하는 커뮤니티 서비스")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
