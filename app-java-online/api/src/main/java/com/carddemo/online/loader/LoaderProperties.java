package com.carddemo.online.loader;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carddemo.loader")
public record LoaderProperties(boolean enabled, String dataDir, DataEncoding encoding) {
}
