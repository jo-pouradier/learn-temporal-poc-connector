package com.example.temporal.controller;

import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.temporal.schedule.ScheduleManager;
import io.temporal.client.schedules.ScheduleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

import static org.apache.logging.log4j.util.Strings.isEmpty;

/**
 * REST controller for runtime configuration changes.
 * Allows hot-reloading of queue processing interval.
 */
@RestController
@RequestMapping("/config")
public class ConfigController {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigController.class);

    private final ConnectorProperties properties;
    private final ScheduleClient scheduleClient;
    private final ScheduleManager scheduleManager;

    public ConfigController(ConnectorProperties properties, ScheduleClient scheduleClient, ScheduleManager scheduleManager) {
        this.properties = properties;
        this.scheduleClient = scheduleClient;
        this.scheduleManager = scheduleManager;
    }

    /**
     * Get current queue processing interval.
     */
    @GetMapping("/queue-interval")
    public ResponseEntity<Map<String, Object>> getQueueInterval() {
        return ResponseEntity.ok(Map.of(
            "interval", properties.getQueueProcessingInterval()
        ));
    }

    @PostMapping("/queue-interval")
    public ResponseEntity<Map<String, Object>> setQueueInterval(
            @RequestParam(required = false) String scheduleId,
            @RequestParam Duration interval
    ) {
        Duration oldInterval = properties.getQueueProcessingInterval();
        properties.setQueueProcessingInterval(interval);

        if (isEmpty(scheduleId)) {
            scheduleManager.reschedule(ScheduleManager.PRICE_SCHEDULE_ID, interval);
            scheduleManager.reschedule(ScheduleManager.STOCK_SCHEDULE_ID, interval);
        } else {
            scheduleManager.reschedule(scheduleId, interval);
        }
        LOG.info("Queue processing interval changed: {} -> {}", oldInterval, interval);

        return ResponseEntity.ok(Map.of(
            "previousInterval", oldInterval,
            "newInterval", interval,
            "message", "Queue processing interval updated"
        ));
    }

    /**
     * Get all runtime-configurable properties.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllConfig() {
        return ResponseEntity.ok(Map.of(
            "queueProcessingIntervalMs", properties.getQueueProcessingInterval(),
            "queueSize", properties.getQueueSize()
        ));
    }
}
