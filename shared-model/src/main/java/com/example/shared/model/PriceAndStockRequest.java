package com.example.shared.model;

public record PriceAndStockRequest(String orderId, int price, int stock) {

    public PriceAndStockRequest() {
        this(null, 0, 0);
    }

    /**
     * Constructor for backward compatibility (connector-app, channel-app).
     */
    public PriceAndStockRequest(int price, int stock) {
        this(null, price, stock);
    }
}
