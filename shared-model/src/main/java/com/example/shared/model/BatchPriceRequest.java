package com.example.shared.model;

import java.util.List;

public record BatchPriceRequest(List<PriceRequest> data) {
    
    public BatchPriceRequest() {
        this(List.of());
    }
}
