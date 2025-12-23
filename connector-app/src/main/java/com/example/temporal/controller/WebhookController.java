package com.example.temporal.controller;

import com.example.shared.model.CallbackResponse;
import com.example.shared.model.PriceAndStockRequest;
import com.example.temporal.service.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class WebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookController.class);
    private final RequestService requestService;

    public WebhookController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping("/webhook/priceAndStock")
    public ResponseEntity<?> handlePriceAndStock(
            @RequestBody PriceAndStockRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return requestService.processPriceAndStock(request, correlationId);
    }

    @PostMapping("/webhook/priceAndStock/batch")
    public ResponseEntity<?> handleBatchPriceAndStock(
            @RequestBody java.util.List<PriceAndStockRequest> requests) {
        return ResponseEntity.accepted().body(requestService.processBatchPriceAndStock(requests));
    }

    @PostMapping("/callback/{orderId}")
    public ResponseEntity<?> handleChannelCallback(
            @PathVariable String orderId,
            @RequestParam(required = false) String type,
            @RequestBody CallbackResponse callbackResponse) {
        return requestService.processCallback(orderId, type, callbackResponse);
    }

    @GetMapping("/status/{orderId}")
    public ResponseEntity<?> getStatus(@PathVariable String orderId) {
        return ResponseEntity.ok(requestService.getStatus(orderId));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getAllStatus(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        // Validate size bounds (1-100)
        size = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(requestService.getAllStatus(filter, page, size));
    }

    @GetMapping("/status/{orderId}/history")
    public ResponseEntity<?> getHistory(@PathVariable String orderId) {
        return ResponseEntity.ok(requestService.getHistory(orderId));
    }

    @DeleteMapping("/queues/clear")
    public ResponseEntity<?> clearQueues() {
        LOG.info("Received request to clear all queues");
        var result = requestService.clearAllQueues();
        return ResponseEntity.ok(result);
    }
}
