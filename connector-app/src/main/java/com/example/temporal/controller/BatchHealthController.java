package com.example.temporal.controller;

import com.example.temporal.service.BatchWorkflowHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for monitoring batch workflow health and status.
 */
@RestController
@RequestMapping("/batch")
public class BatchHealthController {

    private final BatchWorkflowHealthService healthService;

    public BatchHealthController(BatchWorkflowHealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * Health check endpoint for batch workflows.
     * Returns the status of price and stock batch workflows.
     *
     * GET /batch/health
     *
     * Response example:
     * {
     *   "status": "UP",
     *   "priceWorkflow": {
     *     "workflowId": "send-price-updates-global",
     *     "running": true,
     *     "pendingCount": 3,
     *     "batchCounter": 5,
     *     "totalProcessed": 50
     *   },
     *   "stockWorkflow": {
     *     "workflowId": "send-stock-updates-global",
     *     "running": true,
     *     "pendingCount": 0,
     *     "batchCounter": 3,
     *     "totalProcessed": 30
     *   }
     * }
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = healthService.getBatchWorkflowsHealth();
        
        // Return 200 if all workflows are running, 503 if any are down
        boolean allHealthy = "UP".equals(health.get("status"));
        
        return allHealthy 
                ? ResponseEntity.ok(health)
                : ResponseEntity.status(503).body(health);
    }
}
