package com.example.temporal.repository;

import com.example.shared.model.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.temporal.jooq.generated.tables.ConnectorRequestStates.CONNECTOR_REQUEST_STATES;

/**
 * Repository for connector_request_states table.
 * Handles CRUD operations for RequestState persistence.
 * Uses jOOQ generated classes for type-safe queries.
 */
@Repository
public class ConnectorStateRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorStateRepository.class);
    
    private final DSLContext dsl;

    public ConnectorStateRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Save or update request state
     */
    public void save(RequestState state) {
        LOG.debug("Saving state for orderId: {}", state.getOrderId());
        
        dsl.insertInto(CONNECTOR_REQUEST_STATES)
            .set(CONNECTOR_REQUEST_STATES.ORDER_ID, state.getOrderId())
            .set(CONNECTOR_REQUEST_STATES.CORRELATION_ID, state.getCorrelationId())
            .set(CONNECTOR_REQUEST_STATES.STATUS, state.getStatus().name())
            .set(CONNECTOR_REQUEST_STATES.SUBMITTED_AT, state.getSubmittedAt())
            .set(CONNECTOR_REQUEST_STATES.ACCEPTED_AT, state.getAcceptedAt())
            .set(CONNECTOR_REQUEST_STATES.COMPLETED_AT, state.getCompletedAt())
            .set(CONNECTOR_REQUEST_STATES.ERROR, state.getError())
            .set(CONNECTOR_REQUEST_STATES.PRICE_COMPLETED, state.isPriceCompleted())
            .set(CONNECTOR_REQUEST_STATES.STOCK_COMPLETED, state.isStockCompleted())
            .set(CONNECTOR_REQUEST_STATES.ORIGINAL_REQUEST_JSON, state.getOriginalRequest())
            .set(CONNECTOR_REQUEST_STATES.PRICE_CALLBACK_JSON, state.getPriceCallback())
            .set(CONNECTOR_REQUEST_STATES.STOCK_CALLBACK_JSON, state.getStockCallback())
            .set(CONNECTOR_REQUEST_STATES.FINAL_RESPONSE_JSON, state.getFinalResponse())
            .set(CONNECTOR_REQUEST_STATES.UPDATED_AT, LocalDateTime.now())
            .onConflict(CONNECTOR_REQUEST_STATES.ORDER_ID)
            .doUpdate()
            .set(CONNECTOR_REQUEST_STATES.STATUS, state.getStatus().name())
            .set(CONNECTOR_REQUEST_STATES.ACCEPTED_AT, state.getAcceptedAt())
            .set(CONNECTOR_REQUEST_STATES.COMPLETED_AT, state.getCompletedAt())
            .set(CONNECTOR_REQUEST_STATES.ERROR, state.getError())
            .set(CONNECTOR_REQUEST_STATES.PRICE_COMPLETED, state.isPriceCompleted())
            .set(CONNECTOR_REQUEST_STATES.STOCK_COMPLETED, state.isStockCompleted())
            .set(CONNECTOR_REQUEST_STATES.PRICE_CALLBACK_JSON, state.getPriceCallback())
            .set(CONNECTOR_REQUEST_STATES.STOCK_CALLBACK_JSON, state.getStockCallback())
            .set(CONNECTOR_REQUEST_STATES.FINAL_RESPONSE_JSON, state.getFinalResponse())
            .set(CONNECTOR_REQUEST_STATES.UPDATED_AT, LocalDateTime.now())
            .execute();
    }

    /**
     * Find by order ID
     */
    public Optional<RequestState> findByOrderId(String orderId) {
        return dsl.selectFrom(CONNECTOR_REQUEST_STATES)
            .where(CONNECTOR_REQUEST_STATES.ORDER_ID.eq(orderId))
            .fetchOptional()
            .map(this::toRequestState);
    }

    /**
     * Find all states
     */
    public List<RequestState> findAll() {
        return dsl.selectFrom(CONNECTOR_REQUEST_STATES)
            .orderBy(CONNECTOR_REQUEST_STATES.CREATED_AT.desc())
            .fetch()
            .map(this::toRequestState);
    }

    /**
     * Delete by order ID
     */
    public void delete(String orderId) {
        dsl.deleteFrom(CONNECTOR_REQUEST_STATES)
            .where(CONNECTOR_REQUEST_STATES.ORDER_ID.eq(orderId))
            .execute();
    }

    /**
     * Count all states
     */
    public int count() {
        return dsl.selectCount()
            .from(CONNECTOR_REQUEST_STATES)
            .fetchOne(0, int.class);
    }

    /**
     * Count states grouped by status
     */
    public java.util.Map<String, Integer> countByStatus() {
        return dsl.select(CONNECTOR_REQUEST_STATES.STATUS, org.jooq.impl.DSL.count())
            .from(CONNECTOR_REQUEST_STATES)
            .groupBy(CONNECTOR_REQUEST_STATES.STATUS)
            .fetchMap(CONNECTOR_REQUEST_STATES.STATUS, org.jooq.impl.DSL.count());
    }

    /**
     * Find all states with pagination and optional status filter.
     * 
     * @param offset number of records to skip
     * @param limit maximum number of records to return
     * @param statusFilter optional status to filter by (case-insensitive)
     * @return paginated list of RequestState
     */
    public List<RequestState> findAllPaginated(int offset, int limit, String statusFilter) {
        if (statusFilter != null && !statusFilter.isEmpty()) {
            return dsl.selectFrom(CONNECTOR_REQUEST_STATES)
                .where(CONNECTOR_REQUEST_STATES.STATUS.equalIgnoreCase(statusFilter))
                .orderBy(CONNECTOR_REQUEST_STATES.CREATED_AT.desc())
                .offset(offset)
                .limit(limit)
                .fetch()
                .map(this::toRequestState);
        }
        
        return dsl.selectFrom(CONNECTOR_REQUEST_STATES)
            .orderBy(CONNECTOR_REQUEST_STATES.CREATED_AT.desc())
            .offset(offset)
            .limit(limit)
            .fetch()
            .map(this::toRequestState);
    }

    /**
     * Count states with optional status filter.
     * 
     * @param statusFilter optional status to filter by (case-insensitive)
     * @return count of matching states
     */
    public int countFiltered(String statusFilter) {
        if (statusFilter != null && !statusFilter.isEmpty()) {
            return dsl.selectCount()
                .from(CONNECTOR_REQUEST_STATES)
                .where(CONNECTOR_REQUEST_STATES.STATUS.equalIgnoreCase(statusFilter))
                .fetchOne(0, int.class);
        }
        return count();
    }

    private RequestState toRequestState(Record r) {
        RequestState state = new RequestState();
        state.setOrderId(r.get(CONNECTOR_REQUEST_STATES.ORDER_ID));
        state.setCorrelationId(r.get(CONNECTOR_REQUEST_STATES.CORRELATION_ID));
        state.setStatus(OrderStatus.valueOf(r.get(CONNECTOR_REQUEST_STATES.STATUS)));
        state.setSubmittedAt(r.get(CONNECTOR_REQUEST_STATES.SUBMITTED_AT));
        state.setAcceptedAt(r.get(CONNECTOR_REQUEST_STATES.ACCEPTED_AT));
        state.setCompletedAt(r.get(CONNECTOR_REQUEST_STATES.COMPLETED_AT));
        state.setError(r.get(CONNECTOR_REQUEST_STATES.ERROR));
        state.setPriceCompleted(r.get(CONNECTOR_REQUEST_STATES.PRICE_COMPLETED));
        state.setStockCompleted(r.get(CONNECTOR_REQUEST_STATES.STOCK_COMPLETED));
        state.setOriginalRequest(r.get(CONNECTOR_REQUEST_STATES.ORIGINAL_REQUEST_JSON));
        state.setPriceCallback(r.get(CONNECTOR_REQUEST_STATES.PRICE_CALLBACK_JSON));
        state.setStockCallback(r.get(CONNECTOR_REQUEST_STATES.STOCK_CALLBACK_JSON));
        state.setFinalResponse(r.get(CONNECTOR_REQUEST_STATES.FINAL_RESPONSE_JSON));
        return state;
    }
}
