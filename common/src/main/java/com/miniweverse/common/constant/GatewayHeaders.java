package com.miniweverse.common.constant;

/**
 * API Gateway가 인증된 요청에 실어 보내는 헤더 이름.
 */
public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";

    private GatewayHeaders() {
    }
}
