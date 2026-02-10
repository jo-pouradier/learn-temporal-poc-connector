package com.example.channel.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for rate limiting using Bucket4j.
 * Provides a token bucket for controlling request rates to the stock batch endpoint.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Creates a rate limiter bucket for stock batch requests.
     * Configured for 0.5 req/s (1 token every 2 seconds).
     *
     * @param properties channel configuration properties
     * @return configured Bucket instance
     */
    @Bean
    public Bucket stockBatchRateLimiter(ChannelProperties properties) {
        // 0.5 req/s = 1 token refilled every 2 seconds
        Bandwidth limit = Bandwidth.builder()
                .capacity(properties.getStockBatchRateLimitCapacity())
                .refillIntervally(
                        properties.getStockBatchRateLimitRefillTokens(),
                        Duration.ofMillis(properties.getStockBatchRateLimitRefillMillis()))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
