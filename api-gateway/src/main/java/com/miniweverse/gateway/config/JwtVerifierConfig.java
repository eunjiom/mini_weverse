package com.miniweverse.gateway.config;

import com.miniweverse.common.security.jwt.JwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtVerifierConfig {

    @Bean
    public JwtVerifier jwtVerifier(@Value("${jwt.public-key-path}") String publicKeyPath) {
        return new JwtVerifier(publicKeyPath);
    }
}
