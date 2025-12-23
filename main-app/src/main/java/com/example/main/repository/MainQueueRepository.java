package com.example.main.repository;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.main.jooq.generated.tables.MainBatchQueue.MAIN_BATCH_QUEUE;

/**
 * Repository for main_batch_queue table.
 * Handles FIFO queue operations for batch processing.
 * Uses jOOQ generated classes for type-safe queries.
 */
@Repository
public class MainQueueRepository {

    private static final Logger LOG = LoggerFactory.getLogger(MainQueueRepository.class);
    
    private final DSLContext dsl;

    public MainQueueRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Add order to the batch queue
     */
    public void enqueue(String orderId) {
        LOG.debug("Enqueuing orderId: {}", orderId);
        
        dsl.insertInto(MAIN_BATCH_QUEUE)
            .set(MAIN_BATCH_QUEUE.ORDER_ID, orderId)
            .onConflict(MAIN_BATCH_QUEUE.ORDER_ID)
            .doNothing()  // Ignore if already queued
            .execute();
    }

    /**
     * Dequeue up to 'limit' orders (FIFO - oldest first)
     * Returns list of order IDs and removes them from queue
     */
    public List<String> dequeue(int limit) {
        LOG.debug("Dequeuing up to {} orders", limit);
        
        // Select oldest entries
        List<String> orderIds = dsl.select(MAIN_BATCH_QUEUE.ORDER_ID)
            .from(MAIN_BATCH_QUEUE)
            .orderBy(MAIN_BATCH_QUEUE.QUEUED_AT)
            .limit(limit)
            .fetch(MAIN_BATCH_QUEUE.ORDER_ID);

        // Delete dequeued items
        if (!orderIds.isEmpty()) {
            dsl.deleteFrom(MAIN_BATCH_QUEUE)
                .where(MAIN_BATCH_QUEUE.ORDER_ID.in(orderIds))
                .execute();
            
            LOG.debug("Dequeued {} orders", orderIds.size());
        }
        
        return orderIds;
    }

    /**
     * Clear all items from queue
     */
    public void clear() {
        int deleted = dsl.deleteFrom(MAIN_BATCH_QUEUE).execute();
        LOG.debug("Cleared {} items from queue", deleted);
    }

    /**
     * Get current queue size
     */
    public int size() {
        return dsl.selectCount()
            .from(MAIN_BATCH_QUEUE)
            .fetchOne(0, int.class);
    }

    /**
     * Check if order is in queue
     */
    public boolean contains(String orderId) {
        return dsl.selectCount()
            .from(MAIN_BATCH_QUEUE)
            .where(MAIN_BATCH_QUEUE.ORDER_ID.eq(orderId))
            .fetchOne(0, int.class) > 0;
    }

    /**
     * Get the oldest queued_at timestamp (for lag calculation)
     * Returns null if queue is empty
     */
    public java.time.LocalDateTime getOldestQueuedAt() {
        return dsl.select(org.jooq.impl.DSL.min(MAIN_BATCH_QUEUE.QUEUED_AT))
            .from(MAIN_BATCH_QUEUE)
            .fetchOne(0, java.time.LocalDateTime.class);
    }
}
