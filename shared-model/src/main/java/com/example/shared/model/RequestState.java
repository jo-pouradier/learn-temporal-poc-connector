package com.example.shared.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RequestState {

    private String orderId;
    private String correlationId;
    private PriceAndStockRequest originalRequest;
    private OrderStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private CallbackResponse finalResponse;
    private String error;
    private CallbackResponse priceCallback;
    private CallbackResponse stockCallback;
    private boolean priceCompleted = false;
    private boolean stockCompleted = false;
    private final List<StateEvent> eventHistory = Collections.synchronizedList(new ArrayList<>());

    public RequestState() {
    }

    /**
     * Constructor for main-app (orderId as primary key).
     */
    public RequestState(String orderId, String correlationId, PriceAndStockRequest originalRequest) {
        this.orderId = orderId;
        this.correlationId = correlationId;
        this.originalRequest = originalRequest;
        this.status = OrderStatus.PENDING;
        this.submittedAt = LocalDateTime.now();
        addEvent(EventType.REQUEST_RECEIVED, OrderStatus.PENDING, "Request created");
    }

    public void resetFlags() {
        this.setPriceCompleted(false);
        this.setStockCompleted(false);
        this.setPriceCallback(null);
        this.setStockCallback(null);
    }

    public StateEvent updatePrice(CallbackResponse callback) {
        this.setPriceCallback(callback);
        this.setPriceCompleted(true);
        return new StateEvent(EventType.CALLBACK_RECEIVED, OrderStatus.PROCESSING_PRICE,
                "Price callback: isPriceOk=" + callback.isPriceOk());
    }

    public StateEvent updateStock(CallbackResponse callback) {
        this.setStockCallback(callback);
        this.setStockCompleted(true);
        return new StateEvent(EventType.CALLBACK_RECEIVED, OrderStatus.PROCESSING_STOCK,
                "Stock callback: isStockOk=" + callback.isStockOk());
    }

    /**
     * Constructor for connector-app backward compatibility (correlationId only).
     */
    public RequestState(String correlationId, PriceAndStockRequest originalRequest) {
        this.correlationId = correlationId;
        this.originalRequest = originalRequest;
        this.status = OrderStatus.PENDING;
        this.submittedAt = LocalDateTime.now();
        addEvent(EventType.REQUEST_RECEIVED, OrderStatus.PENDING, "Request created");
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public PriceAndStockRequest getOriginalRequest() {
        return originalRequest;
    }

    public void setOriginalRequest(PriceAndStockRequest originalRequest) {
        this.originalRequest = originalRequest;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public CallbackResponse getFinalResponse() {
        return finalResponse;
    }

    public void setFinalResponse(CallbackResponse finalResponse) {
        this.finalResponse = finalResponse;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public CallbackResponse getPriceCallback() {
        return priceCallback;
    }

    public void setPriceCallback(CallbackResponse priceCallback) {
        this.priceCallback = priceCallback;
    }

    public CallbackResponse getStockCallback() {
        return stockCallback;
    }

    public void setStockCallback(CallbackResponse stockCallback) {
        this.stockCallback = stockCallback;
    }

    public boolean isPriceCompleted() {
        return priceCompleted;
    }

    public void setPriceCompleted(boolean priceCompleted) {
        this.priceCompleted = priceCompleted;
    }

    public boolean isStockCompleted() {
        return stockCompleted;
    }

    public void setStockCompleted(boolean stockCompleted) {
        this.stockCompleted = stockCompleted;
    }

    /**
     * Add an event to the history (thread-safe)
     */
    public void addEvent(EventType eventType, OrderStatus status, String details) {
        eventHistory.add(new StateEvent(eventType, status, details));
    }

    /**
     * Add an event without details
     */
    public void addEvent(EventType eventType, OrderStatus status) {
        addEvent(eventType, status, null);
    }

    /**
     * Get event history (returns a copy for immutability)
     */
    public List<StateEvent> getEventHistory() {
        synchronized(eventHistory) {
            return new ArrayList<>(eventHistory);
        }
    }

    @Override
    public String toString() {
        return "RequestState{" +
                "orderId='" + orderId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", status='" + status + '\'' +
                ", submittedAt=" + submittedAt +
                ", acceptedAt=" + acceptedAt +
                ", completedAt=" + completedAt +
                ", error='" + error + '\'' +
                ", priceCompleted=" + priceCompleted +
                ", stockCompleted=" + stockCompleted +
                ", eventCount=" + eventHistory.size() +
                '}';
    }
}
