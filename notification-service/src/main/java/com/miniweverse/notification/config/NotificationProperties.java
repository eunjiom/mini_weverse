package com.miniweverse.notification.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "notification.redis")
@Validated
public record NotificationProperties(@Min(1) long ttlDays) {
}
