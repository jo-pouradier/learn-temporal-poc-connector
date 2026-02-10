package com.example.channel.service;

import com.example.channel.config.ChannelProperties;
import com.example.shared.exception.ValidationException;
import com.example.shared.model.BatchResponse;
import com.example.shared.model.ChannelResponse;
import com.example.shared.model.PriceAndStockProcess;
import com.example.shared.model.PriceAndStockRequest;
import com.example.shared.model.PriceRequest;
import com.example.shared.model.StockRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChannelService {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelService.class);

    private final ChannelProperties properties;
    private final AsyncProcessor asyncProcessor;
    private final Map<String, PriceAndStockProcess> processes = new ConcurrentHashMap<>();
    private final Map<String, ChannelOrderState> orderStore;

    public ChannelService(ChannelProperties properties, AsyncProcessor asyncProcessor, Map<String, ChannelOrderState> orderStore) {
        this.properties = properties;
        this.asyncProcessor = asyncProcessor;
        this.orderStore = orderStore;
    }

    public ResponseEntity<ChannelResponse> processPrice(int price, String orderId, String correlationId, String callbackUrl) {
        LOG.info("Price job received: orderId={}, correlationId={}, price={}, callbackUrl={}", 
                orderId, correlationId, price, callbackUrl);

        if (price < 0 || price > properties.getPriceValidationMax()) {
            LOG.warn("Price validation failed: orderId={}, price={}, max={}", orderId, price, properties.getPriceValidationMax());
            throw new ValidationException("Price must be 0-" + properties.getPriceValidationMax());
        }

        // Mark price as processing
        orderStore.compute(orderId, (k, existingState) -> {
            Integer existingPrice = existingState != null ? existingState.price() : null;
            Integer existingStock = existingState != null ? existingState.stock() : null;
            String existingStockStatus = existingState != null ? existingState.stockStatus() : null;
            return new ChannelOrderState(existingPrice, existingStock, "processing", existingStockStatus, LocalDateTime.now());
        });

        PriceAndStockProcess process = new PriceAndStockProcess(orderId, correlationId, "price", 
                new PriceAndStockRequest(price, 0), callbackUrl);
        processes.put(orderId + "_price", process);

        LOG.info("Starting async price processing: orderId={}, correlationId={}", orderId, correlationId);
        asyncProcessor.processAsync(process, "price", price);

        return ResponseEntity.accepted()
                .header("X-Correlation-Id", correlationId)
                .body(new ChannelResponse("accepted", "price"));
    }

    public ResponseEntity<ChannelResponse> processStock(int stock, String orderId, String correlationId, String callbackUrl) {
        LOG.info("Stock job received: orderId={}, correlationId={}, stock={}, callbackUrl={}", 
                orderId, correlationId, stock, callbackUrl);

        if (stock < 0 || stock > properties.getStockValidationMax()) {
            LOG.warn("Stock validation failed: orderId={}, stock={}, max={}", orderId, stock, properties.getStockValidationMax());
            throw new ValidationException("Stock must be 0-" + properties.getStockValidationMax());
        }

        // Mark stock as processing
        orderStore.compute(orderId, (k, existingState) -> {
            Integer existingPrice = existingState != null ? existingState.price() : null;
            Integer existingStock = existingState != null ? existingState.stock() : null;
            String existingPriceStatus = existingState != null ? existingState.priceStatus() : null;
            return new ChannelOrderState(existingPrice, existingStock, existingPriceStatus, "processing", LocalDateTime.now());
        });

        PriceAndStockProcess process = new PriceAndStockProcess(orderId, correlationId, "stock", 
                new PriceAndStockRequest(0, stock), callbackUrl);
        processes.put(orderId + "_stock", process);

        LOG.info("Starting async stock processing: orderId={}, correlationId={}", orderId, correlationId);
        asyncProcessor.processAsync(process, "stock", stock);

        return ResponseEntity.accepted()
                .header("X-Correlation-Id", correlationId)
                .body(new ChannelResponse("accepted", "stock"));
    }

    public ChannelOrderState getOrderState(String orderId) {
        return orderStore.get(orderId);
    }

    public ResponseEntity<BatchResponse> processPriceBatch(List<PriceRequest> priceRequests, String correlationId) {
        LOG.info("Batch price job received: correlationId={}, count={}", 
                correlationId, priceRequests.size());

        int processed = 0;
        int failed = 0;
        List<String> failedOrderIds = new ArrayList<>();

        for (PriceRequest request : priceRequests) {
            try {
                // Each request now has its own callbackUrl
                processPrice(request.price(), request.orderId(), correlationId + "_" + request.orderId(), request.callbackUrl());
                processed++;
            } catch (ValidationException e) {
                LOG.warn("Batch price validation failed: orderId={}, price={}, error={}", 
                        request.orderId(), request.price(), e.getMessage());
                failed++;
                failedOrderIds.add(request.orderId());
            } catch (Exception e) {
                LOG.error("Batch price processing error: orderId={}, error={}", 
                        request.orderId(), e.getMessage());
                failed++;
                failedOrderIds.add(request.orderId());
            }
        }

        LOG.info("Batch price processing completed: total={}, processed={}, failed={}", 
                priceRequests.size(), processed, failed);

        BatchResponse response = new BatchResponse(
                "accepted",
                processed,
                failed,
                failedOrderIds,
                "price"
        );

        return ResponseEntity.accepted()
                .header("X-Correlation-Id", correlationId)
                .body(response);
    }

    public ResponseEntity<BatchResponse> processStockBatch(List<StockRequest> stockRequests, String correlationId) {
        LOG.info("Batch stock job received: correlationId={}, count={}", 
                correlationId, stockRequests.size());

        int processed = 0;
        int failed = 0;
        List<String> failedOrderIds = new ArrayList<>();

        for (StockRequest request : stockRequests) {
            try {
                // Each request now has its own callbackUrl
                processStock(request.stock(), request.orderId(), correlationId + "_" + request.orderId(), request.callbackUrl());
                processed++;
            } catch (ValidationException e) {
                LOG.warn("Batch stock validation failed: orderId={}, stock={}, error={}", 
                        request.orderId(), request.stock(), e.getMessage());
                failed++;
                failedOrderIds.add(request.orderId());
            } catch (Exception e) {
                LOG.error("Batch stock processing error: orderId={}, error={}", 
                        request.orderId(), e.getMessage());
                failed++;
                failedOrderIds.add(request.orderId());
            }
        }

        LOG.info("Batch stock processing completed: total={}, processed={}, failed={}", 
                stockRequests.size(), processed, failed);

        BatchResponse response = new BatchResponse(
                "accepted",
                processed,
                failed,
                failedOrderIds,
                "stock"
        );

        return ResponseEntity.accepted()
                .header("X-Correlation-Id", correlationId)
                .body(response);
    }

    public Map<String, Object> clearAllQueues() {
        LOG.info("Clearing all channel queues and state");
        
        int processesCleared = processes.size();
        int orderStatesCleared = orderStore.size();
        
        processes.clear();
        orderStore.clear();
        
        String timestamp = LocalDateTime.now().toString();
        
        LOG.info("Cleared {} processes, {} order states at {}", 
                processesCleared, orderStatesCleared, timestamp);
        
        return Map.of(
                "processesCleared", processesCleared,
                "orderStatesCleared", orderStatesCleared,
                "asyncQueuePurged", true,
                "timestamp", timestamp
        );
    }
}
