package com.example.main.repository;

import com.example.main.jooq.generated.tables.MainRequestStates;
import com.example.shared.model.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.main.jooq.generated.tables.MainRequestStates.MAIN_REQUEST_STATES;

/**
 * Repository for main_request_states table.
 * Handles CRUD operations for RequestState persistence.
 * Uses jOOQ generated classes for type-safe queries.
 */
@Repository
public class MainStateRepository {

    private static final Logger LOG = LoggerFactory.getLogger(MainStateRepository.class);
    
    private final DSLContext dsl;

    public MainStateRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Save or update request state
     */
    public void save(RequestState state) {
        LOG.debug("Saving state for orderId: {}", state.getOrderId());
        
        dsl.insertInto(MAIN_REQUEST_STATES)
            .set(MAIN_REQUEST_STATES.ORDER_ID, state.getOrderId())
            .set(MAIN_REQUEST_STATES.CORRELATION_ID, state.getCorrelationId())
            .set(MAIN_REQUEST_STATES.STATUS, state.getStatus().name())
            .set(MAIN_REQUEST_STATES.SUBMITTED_AT, state.getSubmittedAt())
            .set(MAIN_REQUEST_STATES.ACCEPTED_AT, state.getAcceptedAt())
            .set(MAIN_REQUEST_STATES.COMPLETED_AT, state.getCompletedAt())
            .set(MAIN_REQUEST_STATES.ERROR, state.getError())
            .set(MAIN_REQUEST_STATES.PRICE_COMPLETED, state.isPriceCompleted())
            .set(MAIN_REQUEST_STATES.STOCK_COMPLETED, state.isStockCompleted())
            .set(MAIN_REQUEST_STATES.ORIGINAL_REQUEST_JSON, state.getOriginalRequest())
            .set(MAIN_REQUEST_STATES.PRICE_CALLBACK_JSON, state.getPriceCallback())
            .set(MAIN_REQUEST_STATES.STOCK_CALLBACK_JSON, state.getStockCallback())
            .set(MAIN_REQUEST_STATES.FINAL_RESPONSE_JSON, state.getFinalResponse())
            .set(MAIN_REQUEST_STATES.UPDATED_AT, LocalDateTime.now())
            .onConflict(MAIN_REQUEST_STATES.ORDER_ID)
            .doUpdate()
            .set(MAIN_REQUEST_STATES.STATUS, state.getStatus().name())
            .set(MAIN_REQUEST_STATES.ACCEPTED_AT, state.getAcceptedAt())
            .set(MAIN_REQUEST_STATES.COMPLETED_AT, state.getCompletedAt())
            .set(MAIN_REQUEST_STATES.ERROR, state.getError())
            .set(MAIN_REQUEST_STATES.PRICE_COMPLETED, state.isPriceCompleted())
            .set(MAIN_REQUEST_STATES.STOCK_COMPLETED, state.isStockCompleted())
            .set(MAIN_REQUEST_STATES.PRICE_CALLBACK_JSON, state.getPriceCallback())
            .set(MAIN_REQUEST_STATES.STOCK_CALLBACK_JSON, state.getStockCallback())
            .set(MAIN_REQUEST_STATES.FINAL_RESPONSE_JSON, state.getFinalResponse())
            .set(MAIN_REQUEST_STATES.UPDATED_AT, LocalDateTime.now())
            .execute();
    }

    /**
     * Find by order ID
     */
    public Optional<RequestState> findByOrderId(String orderId) {
        return dsl.selectFrom(MAIN_REQUEST_STATES)
            .where(MAIN_REQUEST_STATES.ORDER_ID.eq(orderId))
            .fetchOptional()
            .map(this::toRequestState);
    }

    /**
     * Find all states
     */
    public List<RequestState> findAll() {
        return dsl.selectFrom(MAIN_REQUEST_STATES)
            .orderBy(MAIN_REQUEST_STATES.CREATED_AT.desc())
            .fetch()
            .map(this::toRequestState);
    }

    /**
     * Delete by order ID
     */
    public void delete(String orderId) {
        dsl.deleteFrom(MAIN_REQUEST_STATES)
            .where(MAIN_REQUEST_STATES.ORDER_ID.eq(orderId))
            .execute();
    }

    /**
     * Count all states
     */
    public int count() {
        return dsl.selectCount()
            .from(MAIN_REQUEST_STATES)
            .fetchOne(0, int.class);
    }

    /**
     * Count states grouped by status
     */
    public java.util.Map<String, Integer> countByStatus() {
        return dsl.select(MAIN_REQUEST_STATES.STATUS, org.jooq.impl.DSL.count())
            .from(MAIN_REQUEST_STATES)
            .groupBy(MAIN_REQUEST_STATES.STATUS)
            .fetchMap(MAIN_REQUEST_STATES.STATUS, org.jooq.impl.DSL.count());
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
            return dsl.selectFrom(MAIN_REQUEST_STATES)
                .where(MAIN_REQUEST_STATES.STATUS.equalIgnoreCase(statusFilter))
                .orderBy(MAIN_REQUEST_STATES.CREATED_AT.desc())
                .offset(offset)
                .limit(limit)
                .fetch()
                .map(this::toRequestState);
        }
        
        return dsl.selectFrom(MAIN_REQUEST_STATES)
            .orderBy(MAIN_REQUEST_STATES.CREATED_AT.desc())
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
                .from(MAIN_REQUEST_STATES)
                .where(MAIN_REQUEST_STATES.STATUS.equalIgnoreCase(statusFilter))
                .fetchOne(0, int.class);
        }
        return count();
    }

    private RequestState toRequestState(Record r) {
        RequestState state = new RequestState();
        state.setOrderId(r.get(MAIN_REQUEST_STATES.ORDER_ID));
        state.setCorrelationId(r.get(MAIN_REQUEST_STATES.CORRELATION_ID));
        state.setStatus(OrderStatus.valueOf(r.get(MAIN_REQUEST_STATES.STATUS)));
        state.setSubmittedAt(r.get(MAIN_REQUEST_STATES.SUBMITTED_AT));
        state.setAcceptedAt(r.get(MAIN_REQUEST_STATES.ACCEPTED_AT));
        state.setCompletedAt(r.get(MAIN_REQUEST_STATES.COMPLETED_AT));
        state.setError(r.get(MAIN_REQUEST_STATES.ERROR));
        state.setPriceCompleted(r.get(MAIN_REQUEST_STATES.PRICE_COMPLETED));
        state.setStockCompleted(r.get(MAIN_REQUEST_STATES.STOCK_COMPLETED));
        state.setOriginalRequest(r.get(MAIN_REQUEST_STATES.ORIGINAL_REQUEST_JSON));
        state.setPriceCallback(r.get(MAIN_REQUEST_STATES.PRICE_CALLBACK_JSON));
        state.setStockCallback(r.get(MAIN_REQUEST_STATES.STOCK_CALLBACK_JSON));
        state.setFinalResponse(r.get(MAIN_REQUEST_STATES.FINAL_RESPONSE_JSON));
        return state;
    }
}
