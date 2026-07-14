package com.miniweverse.gateway.authz;

public enum AccessType {
    /** 이 필터가 관여하지 않음 (예: 세션 기반으로 별도 인증하는 /admin/**). */
    SKIP,
    /** 인증 없이 누구나 접근 가능. */
    PUBLIC,
    /** 로그인(유효한 JWT)만 있으면 접근 가능, role 무관. */
    AUTHENTICATED,
    /** 로그인 + 지정된 role 중 하나여야 접근 가능. */
    ROLE_RESTRICTED
}
