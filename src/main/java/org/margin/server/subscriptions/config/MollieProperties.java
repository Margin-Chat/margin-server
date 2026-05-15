package org.margin.server.subscriptions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mollie")
public record MollieProperties(
        String apiKey,
        String apiBaseUrl,
        String webhookBaseUrl,
        String redirectBaseUrl,
        String testmode
) {
}