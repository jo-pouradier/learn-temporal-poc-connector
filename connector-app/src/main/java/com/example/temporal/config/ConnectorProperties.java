package com.example.temporal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "connector")
public class ConnectorProperties {

    private String url = "http://localhost:8080/webhook/priceAndStock";
    private String selfCallbackUrl = "http://localhost:8080/callback";
    private String callbackUrlBase = "http://localhost:8082/callback";
    private String channelPriceUrl = "http://localhost:8081/price";
    private String channelStockUrl = "http://localhost:8081/stock";
    private int queueSize = 10000;
    private Duration queueProcessingInterval;
    private int batchSize = 10;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSelfCallbackUrl() {
        return selfCallbackUrl;
    }

    public void setSelfCallbackUrl(String selfCallbackUrl) {
        this.selfCallbackUrl = selfCallbackUrl;
    }

    public String getCallbackUrlBase() {
        return callbackUrlBase;
    }

    public void setCallbackUrlBase(String callbackUrlBase) {
        this.callbackUrlBase = callbackUrlBase;
    }

    public String getChannelPriceUrl() {
        return channelPriceUrl;
    }

    public void setChannelPriceUrl(String channelPriceUrl) {
        this.channelPriceUrl = channelPriceUrl;
    }

    public String getChannelStockUrl() {
        return channelStockUrl;
    }

    public void setChannelStockUrl(String channelStockUrl) {
        this.channelStockUrl = channelStockUrl;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public Duration getQueueProcessingInterval() {
        return queueProcessingInterval;
    }

    /**
     * Set the queue processing interval in milliseconds.
     * Called by Spring during property binding and can be called at runtime.
     */
    public void setQueueProcessingInterval(Duration queueProcessingInterval) {
        this.queueProcessingInterval = queueProcessingInterval;
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Set the batch size for dequeuing price and stock jobs.
     * Valid range: 1 to 1000.
     * @param batchSize the batch size to use
     * @throws IllegalArgumentException if batchSize is outside valid range
     */
    public void setBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Batch size must be between 1 and 1000, got: " + batchSize);
        }
        this.batchSize = batchSize;
    }
}
