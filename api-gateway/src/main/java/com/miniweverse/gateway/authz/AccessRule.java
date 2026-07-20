package com.miniweverse.gateway.authz;

import com.miniweverse.common.security.jwt.Role;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

public record AccessRule(String pathPattern, HttpMethod method, AccessType type, Set<Role> allowedRoles) {

    public static AccessRule skip(String pathPattern) {
        return new AccessRule(pathPattern, null, AccessType.SKIP, Set.of());
    }

    public static AccessRule publicRule(String pathPattern) {
        return new AccessRule(pathPattern, null, AccessType.PUBLIC, Set.of());
    }

    public static AccessRule publicRule(HttpMethod method, String pathPattern) {
        return new AccessRule(pathPattern, method, AccessType.PUBLIC, Set.of());
    }

    public static AccessRule authenticated(String pathPattern) {
        return new AccessRule(pathPattern, null, AccessType.AUTHENTICATED, Set.of());
    }

    public static AccessRule restricted(String pathPattern, Role... roles) {
        return new AccessRule(pathPattern, null, AccessType.ROLE_RESTRICTED, EnumSet.copyOf(Set.of(roles)));
    }

    public boolean matches(HttpMethod requestMethod, String path, AntPathMatcher pathMatcher) {
        if (method != null && !method.equals(requestMethod)) {
            return false;
        }
        return pathMatcher.match(pathPattern, path);
    }
}
