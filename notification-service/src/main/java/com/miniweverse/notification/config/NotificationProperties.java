package com.miniweverse.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.redis")
public record NotificationProperties(long ttlDays) {
}
