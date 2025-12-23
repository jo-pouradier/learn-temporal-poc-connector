package com.example.channel.service;

import com.example.channel.config.ChannelProperties;
import com.example.shared.model.CallbackResponse;
import com.example.shared.model.PriceAndStockProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AsyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessor.class);

    private final ChannelProperties properties;
    private final RestClient restClient;
    private final Map<String, ChannelOrderState> orderStore;

    public AsyncProcessor(ChannelProperties properties, RestClient.Builder restClientBuilder, Map<String, ChannelOrderState> orderStore) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.orderStore = orderStore;
    }

    @Async("taskExecutor")
    public void processAsync(PriceAndStockProcess process, String type, int value) {
        LOG.info("Async processing started [{}]: orderId={}, correlationId={}, type={}, value={}", 
                Thread.currentThread().getName(), process.getOrderId(), process.getCorrelationId(), type, value);
        
        try {
            int sleepMs = ThreadLocalRandom.current().nextInt(9001) + 1000;
            LOG.info("Sleeping for {}ms [{}]: orderId={}, type={}", sleepMs, Thread.currentThread().getName(), process.getOrderId(), type);
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            LOG.warn("Async processing interrupted: orderId={}, type={}", process.getOrderId(), type);
            Thread.currentThread().interrupt();
            return;
        }

        // Random failure 25%
        if (ThreadLocalRandom.current().nextInt(100) < 25) {
            LOG.warn("Random failure simulation triggered [{}]: orderId={}, type={}", Thread.currentThread().getName(), process.getOrderId(), type);
            
            // Update state to failed
            orderStore.compute(process.getOrderId(), (k, existingState) -> {
                Integer existingPrice = existingState != null ? existingState.price() : null;
                Integer existingStock = existingState != null ? existingState.stock() : null;
                String priceStatus = "price".equals(type) ? "failed" : (existingState != null ? existingState.priceStatus() : null);
                String stockStatus = "stock".equals(type) ? "failed" : (existingState != null ? existingState.stockStatus() : null);
                return new ChannelOrderState(existingPrice, existingStock, priceStatus, stockStatus, LocalDateTime.now());
            });
            
            CallbackResponse response = CallbackResponse.builder()
                    .correlationId(process.getCorrelationId())
                    .status("failed")
                    .processedAt(LocalDateTime.now())
                    .type(type)
                    .build();
            sendCallback(process.getCallbackUrl(), response);
            return;
        }

        LOG.info("Async processing completed sleep [{}]: orderId={}, correlationId={}, type={}", 
                Thread.currentThread().getName(), process.getOrderId(), process.getCorrelationId(), type);

        boolean isValid = "price".equals(type) 
                ? (value >= 0 && value <= properties.getPriceValidationMax())
                : (value >= 0 && value <= properties.getStockValidationMax());

        // Update state store with completed status
        if (isValid) {
            orderStore.compute(process.getOrderId(), (k, existingState) -> {
                // Handle partial state updates - preserve existing values when updating the other field
                Integer newPrice = "price".equals(type) 
                    ? Integer.valueOf(value)
                    : (existingState != null ? existingState.price() : null);
                Integer newStock = "stock".equals(type) 
                    ? Integer.valueOf(value)
                    : (existingState != null ? existingState.stock() : null);
                String priceStatus = "price".equals(type) ? "completed" : (existingState != null ? existingState.priceStatus() : null);
                String stockStatus = "stock".equals(type) ? "completed" : (existingState != null ? existingState.stockStatus() : null);
                return new ChannelOrderState(newPrice, newStock, priceStatus, stockStatus, LocalDateTime.now());
            });
            LOG.info("Updated order state [{}]: orderId={}, type={}, value={}, status=completed", Thread.currentThread().getName(), process.getOrderId(), type, value);
        }

        CallbackResponse.Builder builder = CallbackResponse.builder()
                .correlationId(process.getCorrelationId())
                .status("processed")
                .processedAt(LocalDateTime.now())
                .type(type);

        if ("price".equals(type)) {
            builder.price(value).priceOk(isValid);
        } else {
            builder.stock(value).stockOk(isValid);
        }

        CallbackResponse response = builder.build();
        LOG.info("Sending callback [{}]: orderId={}, correlationId={}, type={}, isValid={}, callbackUrl={}", 
                Thread.currentThread().getName(), process.getOrderId(), process.getCorrelationId(), type, isValid, process.getCallbackUrl());

        sendCallback(process.getCallbackUrl(), response);
    }

    private void sendCallback(String callbackUrl, CallbackResponse response) {
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            LOG.error("Callback URL is null or empty: correlationId={}, type={}", response.correlationId(), response.type());
            return;
        }

        LOG.info("Attempting callback [{}]: url={}, correlationId={}, type={}", Thread.currentThread().getName(), callbackUrl, response.correlationId(), response.type());
        
        try {
            restClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response)
                    .retrieve()
                    .toBodilessEntity();
            LOG.info("Callback sent successfully [{}]: correlationId={}, type={}, url={}", Thread.currentThread().getName(), response.correlationId(), response.type(), callbackUrl);
        } catch (Exception e) {
            LOG.error("Callback failed [{}]: correlationId={}, type={}, url={}, error={}", 
                    Thread.currentThread().getName(), response.correlationId(), response.type(), callbackUrl, e.getMessage());
        }
    }
}
