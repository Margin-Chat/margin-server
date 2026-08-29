package org.margin.server.subscriptions.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MollieProperties.class, SubscriptionPricingProperties.class})
public class SubscriptionsConfig {
}