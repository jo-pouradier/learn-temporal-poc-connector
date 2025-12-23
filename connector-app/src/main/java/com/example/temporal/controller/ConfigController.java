package com.example.temporal.controller;

import com.example.shared.exception.ValidationException;
import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.service.QueueProcessorScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for runtime configuration changes.
 * Allows hot-reloading of queue processing interval.
 */
@RestController
@RequestMapping("/config")
public class ConfigController {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigController.class);

    private final ConnectorProperties properties;
    private final QueueProcessorScheduler scheduler;

    public ConfigController(ConnectorProperties properties, QueueProcessorScheduler scheduler) {
        this.properties = properties;
        this.scheduler = scheduler;
    }

    /**
     * Get current queue processing interval.
     */
    @GetMapping("/queue-interval")
    public ResponseEntity<Map<String, Object>> getQueueInterval() {
        var status = scheduler.getStatus();
        return ResponseEntity.ok(Map.of(
            "intervalMs", properties.getQueueProcessingIntervalMs(),
            "schedulerRunning", status.running(),
            "priceQueueActive", status.priceQueueActive(),
            "stockQueueActive", status.stockQueueActive()
        ));
    }

    /**
     * Set queue processing interval at runtime.
     * @param ms new interval in milliseconds (10-60000)
     */
    @PostMapping("/queue-interval")
    public ResponseEntity<Map<String, Object>> setQueueInterval(@RequestParam long ms) {
        if (ms < 10 || ms > 60000) {
            throw new ValidationException("Interval must be between 10ms and 60000ms (1 minute)");
        }

        long oldInterval = properties.getQueueProcessingIntervalMs();
        properties.setQueueProcessingIntervalMs(ms);
        scheduler.reschedule();

        LOG.info("Queue processing interval changed: {}ms -> {}ms", oldInterval, ms);

        return ResponseEntity.ok(Map.of(
            "previousIntervalMs", oldInterval,
            "newIntervalMs", ms,
            "message", "Queue processing interval updated"
        ));
    }

    /**
     * Get all runtime-configurable properties.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllConfig() {
        var schedulerStatus = scheduler.getStatus();
        return ResponseEntity.ok(Map.of(
            "queueProcessingIntervalMs", properties.getQueueProcessingIntervalMs(),
            "queueSize", properties.getQueueSize(),
            "scheduler", Map.of(
                "running", schedulerStatus.running(),
                "intervalMs", schedulerStatus.intervalMs(),
                "priceQueueActive", schedulerStatus.priceQueueActive(),
                "stockQueueActive", schedulerStatus.stockQueueActive()
            )
        ));
    }
}
