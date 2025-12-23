package com.example.shared.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OpenTelemetry metrics for request lifecycle monitoring.
 * Provides counters for request events and gauges for request counts by status.
 * 
 * Metric names in Prometheus format:
 * - requests_received_total (counter)
 * - requests_completed_total (counter)
 * - requests_failed_total (counter)
 * - requests_count (gauge with status label)
 * - callbacks_sent_total (counter)
 * - callbacks_received_total (counter)
 * - callbacks_latency (histogram)
 */
public class RequestMetrics {
    
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service");
    private static final AttributeKey<String> STATUS = AttributeKey.stringKey("status");
    
    // Default statuses to pre-initialize for gauge emission
    private static final List<String> DEFAULT_STATUSES = Arrays.asList(
            "QUEUED", "PROCESSING", "AWAITING_PRICE", "AWAITING_STOCK", 
            "AWAITING_CALLBACK", "COMPLETED", "FAILED"
    );
    
    private final String serviceName;
    private final Attributes serviceAttributes;
    private final List<String> knownStatuses;
    
    // Gauge backing stores (per status)
    private final Map<String, AtomicLong> requestCountsByStatus = new ConcurrentHashMap<>();
    
    // Lazy initialization
    private volatile Meter meter;
    private volatile LongCounter requestsReceived;
    private volatile LongCounter requestsCompleted;
    private volatile LongCounter requestsFailed;
    private volatile LongCounter callbacksSent;
    private volatile LongCounter callbacksReceived;
    private volatile DoubleHistogram callbackLatency;
    private volatile boolean initialized = false;
    
    /**
     * Create RequestMetrics with default known statuses for pre-initialization.
     * 
     * @param serviceName Service name for metric labels
     */
    public RequestMetrics(String serviceName) {
        this(serviceName, DEFAULT_STATUSES.toArray(new String[0]));
    }
    
    /**
     * Create RequestMetrics with custom known statuses for pre-initialization.
     * Pre-initializing ensures gauges emit data immediately (with value 0)
     * rather than waiting for first update.
     * 
     * @param serviceName Service name for metric labels
     * @param statuses Known statuses to pre-initialize with value 0
     */
    public RequestMetrics(String serviceName, String... statuses) {
        this.serviceName = serviceName;
        this.serviceAttributes = Attributes.of(SERVICE_NAME, serviceName);
        this.knownStatuses = statuses.length > 0 ? Arrays.asList(statuses) : DEFAULT_STATUSES;
    }
    
    /**
     * Initialize eagerly after Spring context is ready.
     * Pre-initializes known statuses with value 0 to ensure gauges emit data immediately.
     */
    public void init() {
        ensureInitialized();
        // Pre-initialize gauges with 0 for known statuses so they emit data immediately
        for (String status : knownStatuses) {
            requestCountsByStatus.computeIfAbsent(status, k -> new AtomicLong(0));
        }
    }
    
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    meter = GlobalOpenTelemetry.getMeter(serviceName + "-requests");
                    initializeInstruments();
                    initialized = true;
                }
            }
        }
    }
    
    private void initializeInstruments() {
        // Request lifecycle counters
        requestsReceived = meter.counterBuilder("requests.received")
                .setDescription("Total requests received")
                .setUnit("1")
                .build();
        
        requestsCompleted = meter.counterBuilder("requests.completed")
                .setDescription("Total requests completed successfully")
                .setUnit("1")
                .build();
        
        requestsFailed = meter.counterBuilder("requests.failed")
                .setDescription("Total requests failed")
                .setUnit("1")
                .build();
        
        // Callback counters
        callbacksSent = meter.counterBuilder("callbacks.sent")
                .setDescription("Total callbacks sent")
                .setUnit("1")
                .build();
        
        callbacksReceived = meter.counterBuilder("callbacks.received")
                .setDescription("Total callbacks received")
                .setUnit("1")
                .build();
        
        // Latency histogram
        callbackLatency = meter.histogramBuilder("callbacks.latency")
                .setDescription("End-to-end latency from request submission to completion")
                .setUnit("ms")
                .build();
        
        // Observable gauge for request counts by status
        meter.gaugeBuilder("requests.count")
                .setDescription("Count of requests by status")
                .setUnit("1")
                .ofLongs()
                .buildWithCallback(measurement -> {
                    for (Map.Entry<String, AtomicLong> entry : requestCountsByStatus.entrySet()) {
                        Attributes attrs = Attributes.builder()
                                .put(SERVICE_NAME, serviceName)
                                .put(STATUS, entry.getKey())
                                .build();
                        measurement.record(entry.getValue().get(), attrs);
                    }
                });
    }
    
    // --- Request lifecycle methods ---
    
    public void recordRequestReceived() {
        ensureInitialized();
        requestsReceived.add(1, serviceAttributes);
    }
    
    public void recordRequestCompleted() {
        ensureInitialized();
        requestsCompleted.add(1, serviceAttributes);
    }
    
    public void recordRequestFailed() {
        ensureInitialized();
        requestsFailed.add(1, serviceAttributes);
    }
    
    // --- Callback methods ---
    
    public void recordCallbackSent() {
        ensureInitialized();
        callbacksSent.add(1, serviceAttributes);
    }
    
    public void recordCallbackReceived() {
        ensureInitialized();
        callbacksReceived.add(1, serviceAttributes);
    }
    
    public void recordCallbackLatency(Duration duration) {
        ensureInitialized();
        callbackLatency.record(duration.toMillis(), serviceAttributes);
    }
    
    // --- Status count methods (for gauges) ---
    
    /**
     * Update request counts by status from a map.
     * Typically called from a scheduled task.
     */
    public void updateRequestCountsByStatus(Map<String, Integer> statusCounts) {
        ensureInitialized();
        // Clear old values for statuses that no longer exist
        requestCountsByStatus.keySet().removeIf(key -> !statusCounts.containsKey(key));
        
        // Update with new values
        for (Map.Entry<String, Integer> entry : statusCounts.entrySet()) {
            requestCountsByStatus
                    .computeIfAbsent(entry.getKey(), k -> new AtomicLong(0))
                    .set(entry.getValue());
        }
    }
    
    /**
     * Set count for a specific status
     */
    public void setRequestCount(String status, long count) {
        ensureInitialized();
        requestCountsByStatus.computeIfAbsent(status, k -> new AtomicLong(0)).set(count);
    }
}
