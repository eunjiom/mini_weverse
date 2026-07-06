package com.miniweverse.admin.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public record AdminSeedProperties(String seedEmail, String seedPassword) {
}
