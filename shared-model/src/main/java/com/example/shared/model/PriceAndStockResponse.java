package com.example.shared.model;

public record PriceAndStockResponse(String orderId, String status) {

    public PriceAndStockResponse() {
        this(null, null);
    }
}
