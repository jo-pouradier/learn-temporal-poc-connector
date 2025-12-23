package com.example.temporal.service;

import com.example.temporal.config.ConnectorProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages queue processing scheduling with hot-reloadable interval.
 * Replaces @Scheduled annotations to allow runtime interval changes.
 */
@Component
public class QueueProcessorScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(QueueProcessorScheduler.class);

    private final ConnectorProperties properties;
    private final RequestService requestService;
    
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> priceQueueTask;
    private ScheduledFuture<?> stockQueueTask;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long currentIntervalMs = 0;

    public QueueProcessorScheduler(ConnectorProperties properties, RequestService requestService) {
        this.properties = properties;
        this.requestService = requestService;
    }

    @PostConstruct
    public void start() {
        scheduleWithCurrentInterval();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        cancelTasks();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("Queue processor scheduler stopped");
    }

    /**
     * Reschedule tasks with new interval from properties.
     * Called when interval is changed at runtime.
     */
    public synchronized void reschedule() {
        long newInterval = properties.getQueueProcessingIntervalMs();
        if (newInterval != currentIntervalMs) {
            LOG.info("Rescheduling queue processors: {}ms -> {}ms", currentIntervalMs, newInterval);
            cancelTasks();
            scheduleWithCurrentInterval();
        }
    }

    /**
     * Get current scheduling status.
     */
    public synchronized SchedulerStatus getStatus() {
        return new SchedulerStatus(
            running.get(),
            currentIntervalMs,
            priceQueueTask != null && !priceQueueTask.isCancelled(),
            stockQueueTask != null && !stockQueueTask.isCancelled()
        );
    }

    private synchronized void scheduleWithCurrentInterval() {
        currentIntervalMs = properties.getQueueProcessingIntervalMs();
        
        if (currentIntervalMs <= 0) {
            LOG.warn("Invalid queue processing interval: {}ms, using default 1000ms", currentIntervalMs);
            currentIntervalMs = 1000;
        }

        running.set(true);

        priceQueueTask = executor.scheduleAtFixedRate(
            this::processPriceQueueSafe,
            currentIntervalMs,  // initial delay
            currentIntervalMs,  // period
            TimeUnit.MILLISECONDS
        );

        stockQueueTask = executor.scheduleAtFixedRate(
            this::processStockQueueSafe,
            currentIntervalMs,  // initial delay
            currentIntervalMs,  // period
            TimeUnit.MILLISECONDS
        );

        LOG.info("Queue processors scheduled at {}ms interval", currentIntervalMs);
    }

    private void cancelTasks() {
        if (priceQueueTask != null) {
            priceQueueTask.cancel(false);
        }
        if (stockQueueTask != null) {
            stockQueueTask.cancel(false);
        }
    }

    private void processPriceQueueSafe() {
        if (!running.get()) return;
        try {
            requestService.processPriceQueue();
        } catch (Exception e) {
            LOG.error("Error processing price queue: {}", e.getMessage(), e);
        }
    }

    private void processStockQueueSafe() {
        if (!running.get()) return;
        try {
            requestService.processStockQueue();
        } catch (Exception e) {
            LOG.error("Error processing stock queue: {}", e.getMessage(), e);
        }
    }

    /**
     * Status record for scheduler state.
     */
    public record SchedulerStatus(
        boolean running,
        long intervalMs,
        boolean priceQueueActive,
        boolean stockQueueActive
    ) {}
}
