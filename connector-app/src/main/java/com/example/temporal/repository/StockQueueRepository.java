package com.example.temporal.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.temporal.jooq.generated.tables.ConnectorStockQueue.CONNECTOR_STOCK_QUEUE;

/**
 * Repository for connector_stock_queue table.
 * Handles FIFO queue operations for stock validation jobs.
 * Uses jOOQ generated classes for type-safe queries.
 */
@Repository
public class StockQueueRepository {

    private static final Logger LOG = LoggerFactory.getLogger(StockQueueRepository.class);
    
    private final DSLContext dsl;

    public StockQueueRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Add stock job to the queue
     */
    public void enqueue(String orderId, String correlationId, int stock) {
        LOG.debug("Enqueuing stock job for orderId: {}", orderId);
        
        dsl.insertInto(CONNECTOR_STOCK_QUEUE)
            .set(CONNECTOR_STOCK_QUEUE.ORDER_ID, orderId)
            .set(CONNECTOR_STOCK_QUEUE.CORRELATION_ID, correlationId)
            .set(CONNECTOR_STOCK_QUEUE.STOCK, stock)
            .onConflict(CONNECTOR_STOCK_QUEUE.ORDER_ID)
            .doNothing()  // Ignore if already queued
            .execute();
    }

    /**
     * Dequeue up to 'limit' jobs (FIFO - oldest first)
     * Returns list of StockJob and removes them from queue
     */
    public List<StockJob> dequeue(int limit) {
        LOG.debug("Dequeuing up to {} stock jobs", limit);
        
        // Select oldest entries
        List<StockJob> jobs = dsl.select(
                CONNECTOR_STOCK_QUEUE.ORDER_ID,
                CONNECTOR_STOCK_QUEUE.CORRELATION_ID,
                CONNECTOR_STOCK_QUEUE.STOCK)
            .from(CONNECTOR_STOCK_QUEUE)
            .orderBy(CONNECTOR_STOCK_QUEUE.QUEUED_AT)
            .limit(limit)
            .fetch(this::toStockJob);

        // Delete dequeued items
        if (!jobs.isEmpty()) {
            List<String> orderIds = jobs.stream().map(StockJob::orderId).toList();
            dsl.deleteFrom(CONNECTOR_STOCK_QUEUE)
                .where(CONNECTOR_STOCK_QUEUE.ORDER_ID.in(orderIds))
                .execute();
            
            LOG.debug("Dequeued {} stock jobs", jobs.size());
        }
        
        return jobs;
    }

    /**
     * Clear all items from queue
     */
    public void clear() {
        int deleted = dsl.deleteFrom(CONNECTOR_STOCK_QUEUE).execute();
        LOG.debug("Cleared {} items from stock queue", deleted);
    }

    /**
     * Get current queue size
     */
    public int size() {
        return dsl.selectCount()
            .from(CONNECTOR_STOCK_QUEUE)
            .fetchOne(0, int.class);
    }

    /**
     * Check if order is in queue
     */
    public boolean contains(String orderId) {
        return dsl.selectCount()
            .from(CONNECTOR_STOCK_QUEUE)
            .where(CONNECTOR_STOCK_QUEUE.ORDER_ID.eq(orderId))
            .fetchOne(0, int.class) > 0;
    }

    /**
     * Get the oldest queued_at timestamp (for lag calculation)
     * Returns null if queue is empty
     */
    public java.time.LocalDateTime getOldestQueuedAt() {
        return dsl.select(org.jooq.impl.DSL.min(CONNECTOR_STOCK_QUEUE.QUEUED_AT))
            .from(CONNECTOR_STOCK_QUEUE)
            .fetchOne(0, java.time.LocalDateTime.class);
    }

    private StockJob toStockJob(Record r) {
        Integer stockValue = r.get(CONNECTOR_STOCK_QUEUE.STOCK);
        return new StockJob(
            r.get(CONNECTOR_STOCK_QUEUE.ORDER_ID),
            r.get(CONNECTOR_STOCK_QUEUE.CORRELATION_ID),
            stockValue != null ? stockValue : 0
        );
    }

    /**
     * Simple record to represent a stock job
     */
    public record StockJob(String orderId, String correlationId, int stock) {}
}
