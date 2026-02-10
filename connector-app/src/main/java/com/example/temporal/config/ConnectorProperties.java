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
    private String channelPriceBatchUrl = "http://localhost:8081/price/batch";
    private String channelStockBatchUrl = "http://localhost:8081/stock/batch";

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

    public String getChannelPriceBatchUrl() {
        return channelPriceBatchUrl;
    }

    public void setChannelPriceBatchUrl(String channelPriceBatchUrl) {
        this.channelPriceBatchUrl = channelPriceBatchUrl;
    }

    public String getChannelStockBatchUrl() {
        return channelStockBatchUrl;
    }

    public void setChannelStockBatchUrl(String channelStockBatchUrl) {
        this.channelStockBatchUrl = channelStockBatchUrl;
    }

}
