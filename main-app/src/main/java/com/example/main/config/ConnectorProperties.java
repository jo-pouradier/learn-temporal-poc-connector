package com.example.main.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "connector")
public class ConnectorProperties {

    private String url = "http://localhost:8080";
    private String callbackUrlBase = "http://localhost:8082/callback";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCallbackUrlBase() {
        return callbackUrlBase;
    }

    public void setCallbackUrlBase(String callbackUrlBase) {
        this.callbackUrlBase = callbackUrlBase;
    }
}
