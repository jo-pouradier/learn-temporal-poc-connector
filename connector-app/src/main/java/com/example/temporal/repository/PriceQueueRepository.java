package com.example.temporal.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

import static com.example.temporal.jooq.generated.tables.ConnectorPriceQueue.CONNECTOR_PRICE_QUEUE;

/**
 * Repository for connector_price_queue table.
 * Handles FIFO queue operations for price validation jobs.
 * Uses jOOQ generated classes for type-safe queries.
 */
@Repository
public class PriceQueueRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PriceQueueRepository.class);
    
    private final DSLContext dsl;

    public PriceQueueRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Add price job to the queue
     */
    public void enqueue(String orderId, String correlationId, int price) {
        LOG.debug("Enqueuing price job for orderId: {}", orderId);
        
        dsl.insertInto(CONNECTOR_PRICE_QUEUE)
            .set(CONNECTOR_PRICE_QUEUE.ORDER_ID, orderId)
            .set(CONNECTOR_PRICE_QUEUE.CORRELATION_ID, correlationId)
            .set(CONNECTOR_PRICE_QUEUE.PRICE, BigDecimal.valueOf(price))
            .onConflict(CONNECTOR_PRICE_QUEUE.ORDER_ID)
            .doNothing()  // Ignore if already queued
            .execute();
    }

    /**
     * Dequeue up to 'limit' jobs (FIFO - oldest first)
     * Returns list of PriceJob and removes them from queue
     */
    public List<PriceJob> dequeue(int limit) {
        LOG.debug("Dequeuing up to {} price jobs", limit);
        
        // Select oldest entries
        List<PriceJob> jobs = dsl.select(
                CONNECTOR_PRICE_QUEUE.ORDER_ID,
                CONNECTOR_PRICE_QUEUE.CORRELATION_ID,
                CONNECTOR_PRICE_QUEUE.PRICE)
            .from(CONNECTOR_PRICE_QUEUE)
            .orderBy(CONNECTOR_PRICE_QUEUE.QUEUED_AT)
            .limit(limit)
            .fetch(this::toPriceJob);

        // Delete dequeued items
        if (!jobs.isEmpty()) {
            List<String> orderIds = jobs.stream().map(PriceJob::orderId).toList();
            dsl.deleteFrom(CONNECTOR_PRICE_QUEUE)
                .where(CONNECTOR_PRICE_QUEUE.ORDER_ID.in(orderIds))
                .execute();
            
            LOG.debug("Dequeued {} price jobs", jobs.size());
        }
        
        return jobs;
    }

    /**
     * Clear all items from queue
     */
    public void clear() {
        int deleted = dsl.deleteFrom(CONNECTOR_PRICE_QUEUE).execute();
        LOG.debug("Cleared {} items from price queue", deleted);
    }

    /**
     * Get current queue size
     */
    public int size() {
        return dsl.selectCount()
            .from(CONNECTOR_PRICE_QUEUE)
            .fetchOne(0, int.class);
    }

    /**
     * Check if order is in queue
     */
    public boolean contains(String orderId) {
        return dsl.selectCount()
            .from(CONNECTOR_PRICE_QUEUE)
            .where(CONNECTOR_PRICE_QUEUE.ORDER_ID.eq(orderId))
            .fetchOne(0, int.class) > 0;
    }

    /**
     * Get the oldest queued_at timestamp (for lag calculation)
     * Returns null if queue is empty
     */
    public java.time.LocalDateTime getOldestQueuedAt() {
        return dsl.select(org.jooq.impl.DSL.min(CONNECTOR_PRICE_QUEUE.QUEUED_AT))
            .from(CONNECTOR_PRICE_QUEUE)
            .fetchOne(0, java.time.LocalDateTime.class);
    }

    private PriceJob toPriceJob(Record r) {
        BigDecimal priceValue = r.get(CONNECTOR_PRICE_QUEUE.PRICE);
        return new PriceJob(
            r.get(CONNECTOR_PRICE_QUEUE.ORDER_ID),
            r.get(CONNECTOR_PRICE_QUEUE.CORRELATION_ID),
            priceValue != null ? priceValue.intValue() : 0
        );
    }

    /**
     * Simple record to represent a price job
     */
    public record PriceJob(String orderId, String correlationId, int price) {}
}
