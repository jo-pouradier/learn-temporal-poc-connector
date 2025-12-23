package com.example.main.controller;

import com.example.main.config.ConnectorProperties;
import com.example.main.service.RequestService;
import com.example.shared.model.CallbackResponse;
import com.example.shared.model.PriceAndStockRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@RestController
public class MainController {

    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);
    private final RequestService requestService;
    private final RestClient restClient;

    public MainController(RequestService requestService, ConnectorProperties properties, RestClient.Builder restClientBuilder) {
        this.requestService = requestService;
        this.restClient = restClientBuilder.build();
    }

    @PostMapping("/priceAndStock")
    public ResponseEntity<?> priceAndStock(@RequestBody PriceAndStockRequest request) {
        LOG.info("Received priceAndStock request: {}", request);
        return requestService.processPriceAndStock(request);
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

    @PostMapping("/callback/{orderId}")
    public ResponseEntity<?> callback(@PathVariable String orderId, @RequestBody CallbackResponse callbackResponse) {
        return ResponseEntity.ok(requestService.processCallback(orderId, callbackResponse));
    }

    @PostMapping("/burst/{count}")
    public ResponseEntity<?> burst(@PathVariable int count) {
        return ResponseEntity.accepted().body(requestService.processBurst(count));
    }

    @PostMapping("/background-load/{action}")
    public ResponseEntity<?> backgroundLoad(@PathVariable String action) {
        return ResponseEntity.ok(requestService.toggleBackgroundLoad(action));
    }

    /**
     * Start load generation at specified rate (requests per second).
     * Example: POST /load/start?rate=50
     */
    @PostMapping("/load/start")
    public ResponseEntity<?> startLoad(@RequestParam(defaultValue = "10") int rate) {
        return ResponseEntity.ok(requestService.startLoad(rate));
    }

    /**
     * Stop load generation.
     */
    @PostMapping("/load/stop")
    public ResponseEntity<?> stopLoad() {
        return ResponseEntity.ok(requestService.stopLoad());
    }

    /**
     * Get current load generator status.
     */
    @GetMapping("/load/status")
    public ResponseEntity<?> loadStatus() {
        return ResponseEntity.ok(requestService.getLoadStatus());
    }

    @DeleteMapping("/queues")
    public ResponseEntity<?> clearAllQueues() {
        LOG.info("Received request to clear all queues across all services");
        
        Map<String, Object> result = new HashMap<>();
        int totalCleared = 0;
        
        // Clear local main-app queue and state
        Map<String, Object> mainAppResult = requestService.clearLocalQueue();
        result.put("mainApp", mainAppResult);
        totalCleared += (int) mainAppResult.getOrDefault("batchQueueCleared", 0);
        totalCleared += (int) mainAppResult.getOrDefault("statesCleared", 0);
        
        // Clear connector-app queues
        try {
            Map connectorResult = restClient.delete()
                    .uri("http://localhost:8080/queues/clear")
                    .retrieve()
                    .body(Map.class);
            result.put("connectorApp", connectorResult);
            if (connectorResult != null) {
                totalCleared += (int) connectorResult.getOrDefault("priceQueueCleared", 0);
                totalCleared += (int) connectorResult.getOrDefault("stockQueueCleared", 0);
                totalCleared += (int) connectorResult.getOrDefault("statesCleared", 0);
            }
        } catch (Exception e) {
            LOG.error("Failed to clear connector-app queues: {}", e.getMessage());
            result.put("connectorApp", Map.of("error", e.getMessage()));
        }
        
        // Clear channel-app queues
        try {
            Map channelResult = restClient.delete()
                    .uri("http://localhost:8081/queues/clear")
                    .retrieve()
                    .body(Map.class);
            result.put("channelApp", channelResult);
            if (channelResult != null) {
                totalCleared += (int) channelResult.getOrDefault("processesCleared", 0);
                totalCleared += (int) channelResult.getOrDefault("orderStatesCleared", 0);
            }
        } catch (Exception e) {
            LOG.error("Failed to clear channel-app queues: {}", e.getMessage());
            result.put("channelApp", Map.of("error", e.getMessage()));
        }
        
        result.put("totalCleared", totalCleared);
        LOG.info("All queues cleared successfully. Total items cleared: {}", totalCleared);
        
        return ResponseEntity.ok(result);
    }
}
