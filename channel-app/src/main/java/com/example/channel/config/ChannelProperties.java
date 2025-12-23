package com.example.channel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "channel")
public class ChannelProperties {

    private String connectorCallbackUrl = "http://localhost:8080/callback";
    private int priceValidationMax = 100000;
    private int stockValidationMax = 10000;

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
}
