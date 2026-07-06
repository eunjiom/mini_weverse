package com.miniweverse.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public record AdminSeedProperties(String seedEmail, String seedPassword) {
}
