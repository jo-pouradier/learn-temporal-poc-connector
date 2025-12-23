package com.example.main.repository;

import com.example.shared.model.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.main.jooq.generated.tables.MainStateEvents.MAIN_STATE_EVENTS;

/**
 * Repository for main_state_events table.
 * Handles event history persistence for request state transitions.
 * Uses jOOQ generated classes for type-safe queries.
 */
@Repository
public class MainEventRepository {

    private static final Logger LOG = LoggerFactory.getLogger(MainEventRepository.class);
    
    private final DSLContext dsl;

    public MainEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Save a new event for an order
     */
    public void save(String orderId, StateEvent event) {
        LOG.debug("Saving event for orderId: {}, eventType: {}", orderId, event.eventType());
        
        dsl.insertInto(MAIN_STATE_EVENTS)
            .set(MAIN_STATE_EVENTS.ORDER_ID, orderId)
            .set(MAIN_STATE_EVENTS.TIMESTAMP, event.timestamp())
            .set(MAIN_STATE_EVENTS.EVENT_TYPE, event.eventType().name())
            .set(MAIN_STATE_EVENTS.STATUS, event.status().name())
            .set(MAIN_STATE_EVENTS.DETAILS, event.details())
            .execute();
    }

    /**
     * Find all events for an order, ordered by timestamp
     */
    public List<StateEvent> findByOrderId(String orderId) {
        return dsl.selectFrom(MAIN_STATE_EVENTS)
            .where(MAIN_STATE_EVENTS.ORDER_ID.eq(orderId))
            .orderBy(MAIN_STATE_EVENTS.TIMESTAMP)
            .fetch()
            .map(this::toStateEvent);
    }

    /**
     * Count events for an order
     */
    public int countByOrderId(String orderId) {
        return dsl.selectCount()
            .from(MAIN_STATE_EVENTS)
            .where(MAIN_STATE_EVENTS.ORDER_ID.eq(orderId))
            .fetchOne(0, int.class);
    }

    /**
     * Delete all events for an order (cascade delete should handle this)
     */
    public void deleteByOrderId(String orderId) {
        dsl.deleteFrom(MAIN_STATE_EVENTS)
            .where(MAIN_STATE_EVENTS.ORDER_ID.eq(orderId))
            .execute();
    }

    /**
     * Delete all events
     */
    public void deleteAll() {
        dsl.deleteFrom(MAIN_STATE_EVENTS).execute();
    }

    private StateEvent toStateEvent(Record r) {
        return new StateEvent(
            r.get(MAIN_STATE_EVENTS.TIMESTAMP),
            EventType.valueOf(r.get(MAIN_STATE_EVENTS.EVENT_TYPE)),
            OrderStatus.valueOf(r.get(MAIN_STATE_EVENTS.STATUS)),
            r.get(MAIN_STATE_EVENTS.DETAILS)
        );
    }
}
