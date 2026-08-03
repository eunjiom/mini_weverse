package com.miniweverse.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * springdoc이 swagger-initializer.js와 /v3/api-docs/swagger-config 안에 만들어내는 URL들은
 * 리버스 프록시 경로 접두어(예: /mini-weverse)를 모른 채로 항상 루트 기준 절대경로로 계산된다.
 * springdoc 내부 확장 지점(SwaggerIndexTransformer)에 커스텀 빈을 꽂아도 실제 요청 처리
 * 경로에서 호출되지 않는 걸 로그로 확인해서, 응답 바디 자체를 가로채는 방식으로 우회한다.
 * SWAGGER_UI_PATH_PREFIX 미설정 시 원본 그대로 통과시킨다.
 */
@Component
public class SwaggerUiPathPrefixFilter implements WebFilter {

    private static final String INITIALIZER_SUFFIX = "swagger-initializer.js";
    private static final String API_DOCS_MARKER = "v3/api-docs";
    private static final Pattern URL_FIELD_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"(/[^\"]*)\"");
    private static final Pattern OAUTH2_REDIRECT_URL_PATTERN =
            Pattern.compile("\"oauth2RedirectUrl\"\\s*:\\s*\"https?://[^\"/]+(/[^\"]*)\"");
    private static final Pattern SERVER_URL_PATTERN =
            Pattern.compile("(\"servers\"\\s*:\\s*\\[\\s*\\{\\s*\"url\"\\s*:\\s*\")https?://[^\"]*(\")");

    @Value("${SWAGGER_UI_PATH_PREFIX:}")
    private String pathPrefix;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        boolean isTarget = path.endsWith(INITIALIZER_SUFFIX) || path.contains(API_DOCS_MARKER);
        if (pathPrefix.isEmpty() || !isTarget) {
            return chain.filter(exchange);
        }

        ServerHttpResponse decoratedResponse = new PathPrefixResponseDecorator(exchange.getResponse(), pathPrefix);
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    private static final class PathPrefixResponseDecorator extends ServerHttpResponseDecorator {

        private final String pathPrefix;

        private PathPrefixResponseDecorator(ServerHttpResponse delegate, String pathPrefix) {
            super(delegate);
            this.pathPrefix = pathPrefix;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            String contentEncoding = getDelegate().getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
            if (contentEncoding != null && !contentEncoding.equalsIgnoreCase("identity")) {
                // 압축된 바이트를 UTF-8 텍스트로 잘못 해석해 깨뜨리지 않도록, 압축 응답은 그대로 통과시킨다.
                return super.writeWith(body);
            }
            return super.writeWith(DataBufferUtils.join(Flux.from(body)).map(this::patch));
        }

        private DataBuffer patch(DataBuffer buffer) {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);

            String content = new String(bytes, StandardCharsets.UTF_8);
            String patched = content
                    // swagger-initializer.js (공백 있는 JS 리터럴 표기)
                    .replace("\"configUrl\" : \"/v3/api-docs/swagger-config\"",
                            "\"configUrl\" : \"" + pathPrefix + "/v3/api-docs/swagger-config\"")
                    // /v3/api-docs/swagger-config 응답 (공백 없는 압축 JSON)
                    .replace("\"configUrl\":\"/v3/api-docs/swagger-config\"",
                            "\"configUrl\":\"" + pathPrefix + "/v3/api-docs/swagger-config\"");
            patched = patchUrlFields(patched);
            patched = patchOauth2RedirectUrl(patched);
            patched = patchServerUrl(patched);
            byte[] patchedBytes = patched.getBytes(StandardCharsets.UTF_8);

            getDelegate().getHeaders().setContentLength(patchedBytes.length);
            return getDelegate().bufferFactory().wrap(patchedBytes);
        }

        /**
         * 단일 문서용 {@code "url":"/v3/api-docs"}뿐 아니라, 드롭다운(urls 배열) 안의
         * {@code "url":"/docs/community/v3/api-docs"} 같은 항목에도 전부 접두어를 붙인다.
         */
        private String patchUrlFields(String content) {
            Matcher matcher = URL_FIELD_PATTERN.matcher(content);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                String path = matcher.group(1);
                matcher.appendReplacement(result, Matcher.quoteReplacement("\"url\":\"" + pathPrefix + path + "\""));
            }
            matcher.appendTail(result);
            return result.toString();
        }

        /**
         * springdoc이 계산하는 {@code oauth2RedirectUrl}은 접두어가 빠진 정도가 아니라,
         * community-service를 가리키는 도커 내부 호스트명(예: {@code http://community-service:8081})이
         * 그대로 절대경로에 노출된다. 외부에서 접근 불가능한 주소라 origin을 통째로 버리고
         * 경로만 살려서 접두어를 붙인다.
         */
        private String patchOauth2RedirectUrl(String content) {
            Matcher matcher = OAUTH2_REDIRECT_URL_PATTERN.matcher(content);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                String path = matcher.group(1);
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement("\"oauth2RedirectUrl\":\"" + pathPrefix + path + "\""));
            }
            matcher.appendTail(result);
            return result.toString();
        }

        /**
         * OpenAPI 문서 최상단 {@code servers} 배열도 도커 내부 호스트명(예:
         * {@code http://community-service:8081})을 그대로 담고 있다. 스웨거 UI의
         * "Try it out"이 이 주소로 실제 요청을 보내므로, 외부에서는 실행 자체가 실패한다.
         * origin을 버리고 접두어로 교체해 현재 페이지 기준 상대경로로 만든다.
         */
        private String patchServerUrl(String content) {
            Matcher matcher = SERVER_URL_PATTERN.matcher(content);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement(matcher.group(1) + pathPrefix + matcher.group(2)));
            }
            matcher.appendTail(result);
            return result.toString();
        }
    }
}
