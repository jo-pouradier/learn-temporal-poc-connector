package com.example.shared.model;

public record StockRequest(String orderId, int stock) {

    public StockRequest() {
        this(null, 0);
    }
}
