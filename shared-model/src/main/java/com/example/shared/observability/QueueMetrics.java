package com.example.shared.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * OpenTelemetry metrics for queue monitoring.
 * Provides gauges for queue size and lag, counters for enqueue/dequeue operations.
 * 
 * Metric names in Prometheus format:
 * - queue_size (gauge with queue label)
 * - queue_lag_seconds (gauge with queue label)
 * - queue_enqueue_total (counter with queue label)
 * - queue_dequeue_total (counter with queue label)
 */
public class QueueMetrics {
    
    private static final AttributeKey<String> QUEUE_NAME = AttributeKey.stringKey("queue");
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service");
    
    private final String serviceName;
    private final Attributes serviceAttributes;
    private final List<String> knownQueues;
    
    // Gauge backing stores (per queue)
    private final Map<String, AtomicLong> queueSizes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> queueLagSeconds = new ConcurrentHashMap<>();
    
    // Lazy initialization
    private volatile Meter meter;
    private volatile LongCounter enqueueCounter;
    private volatile LongCounter dequeueCounter;
    private volatile boolean initialized = false;
    
    /**
     * Create QueueMetrics with known queue names for pre-initialization.
     * Pre-initializing ensures gauges emit data immediately (with value 0) 
     * rather than waiting for first update.
     * 
     * @param serviceName Service name for metric labels
     * @param queueNames Known queue names to pre-initialize with value 0
     */
    public QueueMetrics(String serviceName, String... queueNames) {
        this.serviceName = serviceName;
        this.serviceAttributes = Attributes.of(SERVICE_NAME, serviceName);
        this.knownQueues = Arrays.asList(queueNames);
    }
    
    /**
     * Initialize eagerly after Spring context is ready.
     * Pre-initializes known queues with value 0 to ensure gauges emit data immediately.
     */
    public void init() {
        ensureInitialized();
        // Pre-initialize gauges with 0 for known queues so they emit data immediately
        for (String queue : knownQueues) {
            queueSizes.computeIfAbsent(queue, k -> new AtomicLong(0));
            queueLagSeconds.computeIfAbsent(queue, k -> new AtomicLong(0));
        }
    }
    
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    meter = GlobalOpenTelemetry.getMeter(serviceName + "-queues");
                    initializeInstruments();
                    initialized = true;
                }
            }
        }
    }
    
    private void initializeInstruments() {
        // Counters for enqueue/dequeue operations
        enqueueCounter = meter.counterBuilder("queue.enqueue")
                .setDescription("Total items enqueued")
                .setUnit("1")
                .build();
        
        dequeueCounter = meter.counterBuilder("queue.dequeue")
                .setDescription("Total items dequeued")
                .setUnit("1")
                .build();
        
        // Observable gauges - registered once, values updated via atomic refs
        meter.gaugeBuilder("queue.size")
                .setDescription("Current number of items in queue")
                .setUnit("1")
                .ofLongs()
                .buildWithCallback(measurement -> {
                    for (Map.Entry<String, AtomicLong> entry : queueSizes.entrySet()) {
                        Attributes attrs = Attributes.builder()
                                .put(SERVICE_NAME, serviceName)
                                .put(QUEUE_NAME, entry.getKey())
                                .build();
                        measurement.record(entry.getValue().get(), attrs);
                    }
                });
        
        meter.gaugeBuilder("queue.lag.seconds")
                .setDescription("Age of oldest item in queue (seconds)")
                .setUnit("s")
                .ofLongs()
                .buildWithCallback(measurement -> {
                    for (Map.Entry<String, AtomicLong> entry : queueLagSeconds.entrySet()) {
                        Attributes attrs = Attributes.builder()
                                .put(SERVICE_NAME, serviceName)
                                .put(QUEUE_NAME, entry.getKey())
                                .build();
                        measurement.record(entry.getValue().get(), attrs);
                    }
                });
    }
    
    /**
     * Record an enqueue operation
     */
    public void recordEnqueue(String queueName) {
        recordEnqueue(queueName, 1);
    }
    
    /**
     * Record multiple enqueue operations
     */
    public void recordEnqueue(String queueName, int count) {
        ensureInitialized();
        Attributes attrs = Attributes.builder()
                .put(SERVICE_NAME, serviceName)
                .put(QUEUE_NAME, queueName)
                .build();
        enqueueCounter.add(count, attrs);
    }
    
    /**
     * Record a dequeue operation
     */
    public void recordDequeue(String queueName) {
        recordDequeue(queueName, 1);
    }
    
    /**
     * Record multiple dequeue operations
     */
    public void recordDequeue(String queueName, int count) {
        ensureInitialized();
        Attributes attrs = Attributes.builder()
                .put(SERVICE_NAME, serviceName)
                .put(QUEUE_NAME, queueName)
                .build();
        dequeueCounter.add(count, attrs);
    }
    
    /**
     * Update the current size of a queue (for gauge)
     */
    public void setQueueSize(String queueName, long size) {
        ensureInitialized();
        queueSizes.computeIfAbsent(queueName, k -> new AtomicLong(0)).set(size);
    }
    
    /**
     * Update the queue lag based on oldest queued_at timestamp
     */
    public void updateQueueLag(String queueName, LocalDateTime oldestQueuedAt) {
        ensureInitialized();
        long lagSeconds = 0;
        if (oldestQueuedAt != null) {
            lagSeconds = Duration.between(oldestQueuedAt, LocalDateTime.now()).getSeconds();
            if (lagSeconds < 0) lagSeconds = 0; // Handle clock skew
        }
        queueLagSeconds.computeIfAbsent(queueName, k -> new AtomicLong(0)).set(lagSeconds);
    }
    
    /**
     * Convenience method to update queue metrics from suppliers.
     * Useful for scheduled metric collection.
     */
    public void updateQueueMetrics(String queueName, 
                                    Supplier<Integer> sizeSupplier, 
                                    Supplier<LocalDateTime> oldestQueuedAtSupplier) {
        try {
            setQueueSize(queueName, sizeSupplier.get());
            updateQueueLag(queueName, oldestQueuedAtSupplier.get());
        } catch (Exception e) {
            // Log but don't fail - metrics collection shouldn't break the app
            org.slf4j.LoggerFactory.getLogger(QueueMetrics.class)
                    .warn("Failed to update metrics for queue {}: {}", queueName, e.getMessage());
        }
    }
}
