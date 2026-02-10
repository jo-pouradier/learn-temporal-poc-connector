package com.example.shared.model;

import java.util.List;

public record BatchStockRequest(List<StockRequest> data) {
    
    public BatchStockRequest() {
        this(List.of());
    }
}
