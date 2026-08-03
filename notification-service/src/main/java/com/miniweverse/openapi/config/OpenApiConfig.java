package com.miniweverse.openapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String ERROR_SCHEMA_NAME = "ErrorResponse";

    @Bean
    public OpenAPI notificationServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini Weverse - Notification API")
                        .description("멤버십/채팅 이벤트를 Kafka로 구독해 알림을 생성·조회하는 알림 서비스")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    /**
     * GlobalExceptionHandler(common 모듈)가 모든 엔드포인트에 공통으로 내려주는 에러 응답
     * 포맷(ApiResponse.error)을 문서화한다. 도메인별 구체 에러코드는 서비스마다 달라 전부
     * 나열하기보다, 공통으로 발생 가능한 400/401(인증 필요 API 한정)/500만 예시로 붙인다.
     *
     * ErrorResponse 스키마를 위 OpenAPI 빈의 components에 직접 넣으면 springdoc이 최종
     * 문서를 재계산하는 과정에서 사라진다(실행 중 확인됨) — OpenApiCustomizer는 springdoc이
     * 문서를 다 만든 뒤 마지막에 실행되므로, 여기서 등록해야 실제로 남는다.
     */
    @Bean
    public OpenApiCustomizer commonErrorResponsesCustomizer() {
        return openApi -> {
            openApi.getComponents().addSchemas(ERROR_SCHEMA_NAME, errorResponseSchema());
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        ApiResponses responses = operation.getResponses();
                        addIfAbsent(responses, "400", "COMMON_001", "요청 값이 올바르지 않습니다.");
                        if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
                            addIfAbsent(responses, "401", "COMMON_004", "인증이 필요합니다.");
                        }
                        addIfAbsent(responses, "500", "COMMON_002", "서버 내부 오류가 발생했습니다.");
                    }));
        };
    }

    private void addIfAbsent(ApiResponses responses, String status, String code, String message) {
        if (responses.containsKey(status)) {
            return;
        }

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("success", false);
        example.put("data", null);
        example.put("error", Map.of("code", code, "message", message));

        Schema<?> schema = new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA_NAME);
        MediaType mediaType = new MediaType().schema(schema).example(example);
        responses.addApiResponse(status, new ApiResponse()
                .description(message)
                .content(new Content().addMediaType("application/json", mediaType)));
    }

    private Schema<?> errorResponseSchema() {
        Schema<?> error = new ObjectSchema()
                .addProperty("code", new StringSchema().example("COMMON_001"))
                .addProperty("message", new StringSchema().example("요청 값이 올바르지 않습니다."));

        return new ObjectSchema()
                .addProperty("success", new BooleanSchema().example(false))
                .addProperty("data", new Schema<>().nullable(true))
                .addProperty("error", error);
    }
}
