package com.example.temporal.controller;

import com.example.shared.model.PriceAndStockRequest;
import com.example.shared.model.PriceAndStockResponse;
import com.example.temporal.service.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Test controller for verifying batch processing functionality.
 * This controller provides endpoints to generate test load and verify batching behavior.
 */
@RestController
@RequestMapping("/test")
public class BatchTestController {

    private static final Logger LOG = LoggerFactory.getLogger(BatchTestController.class);

    private final RequestService requestService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public BatchTestController(RequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * Generates test orders to verify batch processing.
     * This endpoint creates multiple price/stock requests to trigger batching.
     *
     * POST /test/generate-orders
     *
     * Request body:
     * {
     *   "count": 15,          // number of orders to create
     *   "delayMs": 100        // optional delay between orders (default: 0)
     * }
     *
     * Response:
     * {
     *   "ordersCreated": 15,
     *   "orderIds": ["order-1", "order-2", ...],
     *   "expectedBatches": 2,  // based on batch size of 10
     *   "message": "Generated 15 test orders. Expected 2 batches (10 + 5 items)"
     * }
     */
    @PostMapping("/generate-orders")
    public ResponseEntity<Map<String, Object>> generateTestOrders(
            @RequestBody Map<String, Integer> request) {
        
        int count = request.getOrDefault("count", 10);
        int delayMs = request.getOrDefault("delayMs", 0);
        
        LOG.info("Generating {} test orders with {}ms delay", count, delayMs);
        
        List<String> orderIds = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String orderId = "test-order-" + UUID.randomUUID().toString().substring(0, 8);
            orderIds.add(orderId);
            
            int finalI = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    if (delayMs > 0 && finalI > 0) {
                        Thread.sleep(delayMs);
                    }
                    
                    PriceAndStockRequest payload = new PriceAndStockRequest(
                            orderId,
                            100 + finalI,  // price
                            50 + finalI    // stock
                    );
                    
                    PriceAndStockResponse response = requestService.processPriceAndStock(
                            payload, "test-correlation-" + finalI);
                    
                    LOG.debug("Created test order {}/{}: {}", finalI + 1, count, orderId);
                    
                } catch (Exception e) {
                    LOG.error("Failed to create test order: {}", orderId, e);
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all orders to be created
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        int expectedBatches = (int) Math.ceil(count / 10.0);
        String message = String.format(
                "Generated %d test orders. Expected %d batches (%s items)",
                count,
                expectedBatches,
                count > 10 ? "10 + " + (count - 10) : count
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("ordersCreated", count);
        response.put("orderIds", orderIds);
        response.put("expectedBatches", expectedBatches);
        response.put("message", message);
        
        LOG.info("Generated {} test orders. Expected {} batches", count, expectedBatches);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Generates a burst of test orders to verify batching under load.
     * This is useful for testing that batching works correctly with high volume.
     *
     * POST /test/burst
     *
     * Request params:
     * - count: number of orders (default: 25)
     *
     * This will create all orders as fast as possible to trigger multiple batches.
     */
    @PostMapping("/burst")
    public ResponseEntity<Map<String, Object>> generateBurst(
            @RequestParam(defaultValue = "25") int count) {
        
        Map<String, Integer> request = new HashMap<>();
        request.put("count", count);
        request.put("delayMs", 0);  // No delay - send all at once
        
        return generateTestOrders(request);
    }

    /**
     * Generates a slow trickle of test orders to verify time-based batching.
     * This is useful for testing that batching works with the 5-second timeout.
     *
     * POST /test/trickle
     *
     * Request params:
     * - count: number of orders (default: 5)
     * - delayMs: delay between orders (default: 500ms)
     *
     * This will create orders slowly to trigger time-based batching.
     */
    @PostMapping("/trickle")
    public ResponseEntity<Map<String, Object>> generateTrickle(
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(defaultValue = "500") int delayMs) {
        
        Map<String, Integer> request = new HashMap<>();
        request.put("count", count);
        request.put("delayMs", delayMs);
        
        return generateTestOrders(request);
    }

    /**
     * Simple ping endpoint to verify the test controller is working.
     *
     * GET /test/ping
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Batch test controller is ready");
        return ResponseEntity.ok(response);
    }
}
