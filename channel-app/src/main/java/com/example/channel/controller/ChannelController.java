package com.example.channel.controller;

import com.example.shared.model.PriceRequest;
import com.example.shared.model.StockRequest;
import com.example.channel.service.ChannelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChannelController {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelController.class);
    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
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
