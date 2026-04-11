package org.margin.server.config.ratelimit;

import io.github.bucket4j.Bandwidth;

import java.time.Duration;

public final class RateLimitConfig {

    private RateLimitConfig() {
    }

    public static Bandwidth createMargin() {
        return Bandwidth.builder()
                .capacity(5)
                .refillGreedy(5, Duration.ofHours(1))
                .build();
    }

    public static Bandwidth register() {
        return Bandwidth.builder()
                .capacity(5)
                .refillGreedy(5, Duration.ofHours(1))
                .build();
    }

    public static Bandwidth uploadImage() {
        return Bandwidth.builder()
                .capacity(3)
                .refillGreedy(3, Duration.ofHours(1))
                .build();
    }
}