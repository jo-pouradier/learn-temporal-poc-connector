package com.example.channel.controller;

import com.example.shared.model.BatchPriceRequest;
import com.example.shared.model.BatchStockRequest;
import com.example.shared.model.PriceRequest;
import com.example.shared.model.StockRequest;
import com.example.channel.service.ChannelService;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChannelController {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelController.class);
    private final ChannelService channelService;
    private final Bucket stockBatchRateLimiter;

    public ChannelController(ChannelService channelService, Bucket stockBatchRateLimiter) {
        this.channelService = channelService;
        this.stockBatchRateLimiter = stockBatchRateLimiter;
    }

    @PostMapping("/price")
    public ResponseEntity<?> price(
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestHeader("X-Callback-Url") String callbackUrl,
            @RequestBody PriceRequest request) {

        LOG.info("Received price request: orderId={}, correlationId={}, price={}, callbackUrl={}", 
                request.orderId(), correlationId, request.price(), callbackUrl);

        return channelService.processPrice(request.price(), request.orderId(), correlationId, callbackUrl);
    }

    @PostMapping("/stock")
    public ResponseEntity<?> stock(
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestHeader("X-Callback-Url") String callbackUrl,
            @RequestBody StockRequest request) {

        LOG.info("Received stock request: orderId={}, correlationId={}, stock={}, callbackUrl={}", 
                request.orderId(), correlationId, request.stock(), callbackUrl);

        return channelService.processStock(request.stock(), request.orderId(), correlationId, callbackUrl);
    }

    @PostMapping("/price/batch")
    public ResponseEntity<?> priceBatch(
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestBody BatchPriceRequest request) {

        LOG.info("Received batch price request: correlationId={}, count={}", 
                correlationId, request.data().size());

        return channelService.processPriceBatch(request.data(), correlationId);
    }

    @PostMapping("/stock/batch")
    public ResponseEntity<?> stockBatch(
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestBody BatchStockRequest request) {

        if (!stockBatchRateLimiter.tryConsume(1)) {
            LOG.warn("Rate limit exceeded for stock batch request: correlationId={}", correlationId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded. Try again later.");
        }

        LOG.info("Received batch stock request: correlationId={}, count={}", 
                correlationId, request.data().size());

        return channelService.processStockBatch(request.data(), correlationId);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrderState(@PathVariable String id) {
        var state = channelService.getOrderState(id);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }

    @DeleteMapping("/queues/clear")
    public ResponseEntity<?> clearQueues() {
        LOG.info("Received request to clear all queues");
        var result = channelService.clearAllQueues();
        return ResponseEntity.ok(result);
    }
}
