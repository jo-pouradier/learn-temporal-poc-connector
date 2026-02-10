package com.example.channel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "channel")
public class ChannelProperties {

    private String connectorCallbackUrl = "http://localhost:8080/callback";
    private int priceValidationMax = 100000;
    private int stockValidationMax = 10000;

    // Rate limiting config for stock batch endpoint (0.5 req/s by default)
    private long stockBatchRateLimitCapacity = 1;
    private long stockBatchRateLimitRefillTokens = 1;
    private long stockBatchRateLimitRefillMillis = 2000; // 2 seconds = 0.5 req/s

    public String getConnectorCallbackUrl() {
        return connectorCallbackUrl;
    }

    public void setConnectorCallbackUrl(String connectorCallbackUrl) {
        this.connectorCallbackUrl = connectorCallbackUrl;
    }

    public int getPriceValidationMax() {
        return priceValidationMax;
    }

    public void setPriceValidationMax(int priceValidationMax) {
        this.priceValidationMax = priceValidationMax;
    }

    public int getStockValidationMax() {
        return stockValidationMax;
    }

    public void setStockValidationMax(int stockValidationMax) {
        this.stockValidationMax = stockValidationMax;
    }

    public long getStockBatchRateLimitCapacity() {
        return stockBatchRateLimitCapacity;
    }

    public void setStockBatchRateLimitCapacity(long stockBatchRateLimitCapacity) {
        this.stockBatchRateLimitCapacity = stockBatchRateLimitCapacity;
    }

    public long getStockBatchRateLimitRefillTokens() {
        return stockBatchRateLimitRefillTokens;
    }

    public void setStockBatchRateLimitRefillTokens(long stockBatchRateLimitRefillTokens) {
        this.stockBatchRateLimitRefillTokens = stockBatchRateLimitRefillTokens;
    }

    public long getStockBatchRateLimitRefillMillis() {
        return stockBatchRateLimitRefillMillis;
    }

    public void setStockBatchRateLimitRefillMillis(long stockBatchRateLimitRefillMillis) {
        this.stockBatchRateLimitRefillMillis = stockBatchRateLimitRefillMillis;
    }
}
