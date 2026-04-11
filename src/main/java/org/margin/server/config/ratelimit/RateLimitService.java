package org.margin.server.config.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


@Service
public class RateLimitService {
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS)
            .maximumSize(50_000)
            .build();

    public boolean tryConsume(String key, Bandwidth bandwidth) {
        Bucket bucket = buckets.get(key, k -> Bucket.builder()
                .addLimit(bandwidth)
                .build());
        return bucket.tryConsume(1);
    }
}