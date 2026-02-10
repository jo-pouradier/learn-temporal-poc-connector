package com.example.shared.model;

public record PriceRequest(String orderId, int price, String callbackUrl) {

    public PriceRequest() {
        this(null, 0, null);
    }
    
    // Constructor for backward compatibility (without callbackUrl)
    public PriceRequest(String orderId, int price) {
        this(orderId, price, null);
    }
}
