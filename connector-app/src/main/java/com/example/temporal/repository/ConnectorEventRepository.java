package com.example.temporal.repository;

import com.example.shared.model.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.temporal.jooq.generated.tables.ConnectorStateEvents.CONNECTOR_STATE_EVENTS;

/**
 * Repository for connector_state_events table.
 * Handles event history persistence for request state transitions.
 * Uses jOOQ generated classes for type-safe queries.
 */
@Repository
public class ConnectorEventRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorEventRepository.class);
    
    private final DSLContext dsl;

    public ConnectorEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Save a new event for an order
     */
    public void save(String orderId, StateEvent event) {
        LOG.debug("Saving event for orderId: {}, eventType: {}", orderId, event.eventType());
        
        dsl.insertInto(CONNECTOR_STATE_EVENTS)
            .set(CONNECTOR_STATE_EVENTS.ORDER_ID, orderId)
            .set(CONNECTOR_STATE_EVENTS.TIMESTAMP, event.timestamp())
            .set(CONNECTOR_STATE_EVENTS.EVENT_TYPE, event.eventType().name())
            .set(CONNECTOR_STATE_EVENTS.STATUS, event.status().name())
            .set(CONNECTOR_STATE_EVENTS.DETAILS, event.details())
            .execute();
    }

    /**
     * Find all events for an order, ordered by timestamp
     */
    public List<StateEvent> findByOrderId(String orderId) {
        return dsl.selectFrom(CONNECTOR_STATE_EVENTS)
            .where(CONNECTOR_STATE_EVENTS.ORDER_ID.eq(orderId))
            .orderBy(CONNECTOR_STATE_EVENTS.TIMESTAMP)
            .fetch()
            .map(this::toStateEvent);
    }


    /**
     * Delete all events
     */
    public void deleteAll() {
        dsl.deleteFrom(CONNECTOR_STATE_EVENTS).execute();
    }

    private StateEvent toStateEvent(Record r) {
        return new StateEvent(
            r.get(CONNECTOR_STATE_EVENTS.TIMESTAMP),
            EventType.valueOf(r.get(CONNECTOR_STATE_EVENTS.EVENT_TYPE)),
            OrderStatus.valueOf(r.get(CONNECTOR_STATE_EVENTS.STATUS)),
            r.get(CONNECTOR_STATE_EVENTS.DETAILS)
        );
    }
}
