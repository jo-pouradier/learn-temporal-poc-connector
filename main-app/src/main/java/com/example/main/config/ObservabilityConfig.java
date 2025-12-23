package com.example.main.config;

import com.example.shared.observability.QueueMetrics;
import com.example.shared.observability.RequestMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean(initMethod = "init")
    public QueueMetrics queueMetrics() {
        return new QueueMetrics("main");
    }

    @Bean(initMethod = "init")
    public RequestMetrics requestMetrics() {
        return new RequestMetrics("main");
    }
}
