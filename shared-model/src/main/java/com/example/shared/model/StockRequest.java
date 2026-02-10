package com.example.shared.model;

public record StockRequest(String orderId, int stock, String callbackUrl) {

    public StockRequest() {
        this(null, 0, null);
    }
    
    // Constructor for backward compatibility (without callbackUrl)
    public StockRequest(String orderId, int stock) {
        this(orderId, stock, null);
    }
}
