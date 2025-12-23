package com.example.temporal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@ConfigurationProperties(prefix = "connector")
public class ConnectorProperties {

    private String url = "http://localhost:8080/webhook/priceAndStock";
    private String selfCallbackUrl = "http://localhost:8080/callback";
    private String callbackUrlBase = "http://localhost:8082/callback";
    private String channelPriceUrl = "http://localhost:8081/price";
    private String channelStockUrl = "http://localhost:8081/stock";
    private int queueSize = 10000;
    
    // Atomic backing for hot-reloadable interval
    private final AtomicLong queueProcessingIntervalMsAtomic = new AtomicLong(100);

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

    /**
     * Get the queue processing interval in milliseconds.
     * This value can be changed at runtime via REST endpoint.
     */
    public long getQueueProcessingIntervalMs() {
        return queueProcessingIntervalMsAtomic.get();
    }

    /**
     * Set the queue processing interval in milliseconds.
     * Called by Spring during property binding and can be called at runtime.
     */
    public void setQueueProcessingIntervalMs(long queueProcessingIntervalMs) {
        this.queueProcessingIntervalMsAtomic.set(queueProcessingIntervalMs);
    }
}
