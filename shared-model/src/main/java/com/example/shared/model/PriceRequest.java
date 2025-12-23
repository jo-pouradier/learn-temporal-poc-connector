package com.example.shared.model;

public record PriceRequest(String orderId, int price) {

    public PriceRequest() {
        this(null, 0);
    }
}
