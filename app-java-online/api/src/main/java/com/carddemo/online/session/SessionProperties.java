package com.carddemo.online.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carddemo.session")
public record SessionProperties(String secret, long ttlMinutes) {
}
