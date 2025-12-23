package com.example.channel.config;

import com.example.channel.service.ChannelOrderState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class OrderStoreConfig {

    @Bean
    public Map<String, ChannelOrderState> orderStore() {
        return new ConcurrentHashMap<>();
    }
}
