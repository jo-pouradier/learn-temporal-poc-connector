package com.example.shared.model;

import java.time.LocalDateTime;

public class PriceAndStockProcess {

    private String orderId;
    private String correlationId;
    private String type;
    private String callbackUrl;
    private LocalDateTime receivedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private String status;
    private CallbackResponse finalCallback;
    private int price;
    private int stock;

    public PriceAndStockProcess() {
    }

    public PriceAndStockProcess(String orderId, String correlationId, String type, PriceAndStockRequest request, String callbackUrl) {
        this.orderId = orderId;
        this.correlationId = correlationId;
        this.type = type;
        this.price = request.price();
        this.stock = request.stock();
        this.callbackUrl = callbackUrl;
        this.status = "pending";
        this.receivedAt = LocalDateTime.now();
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public CallbackResponse getFinalCallback() {
        return finalCallback;
    }

    public void setFinalCallback(CallbackResponse finalCallback) {
        this.finalCallback = finalCallback;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}
