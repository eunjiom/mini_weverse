package com.miniweverse.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String privateKeyPath,
        long accessTokenValidity,
        long refreshTokenValidity
) {
}
