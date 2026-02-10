package com.example.shared.model;

import java.util.List;

public record BatchResponse(
        String status,
        int totalProcessed,
        int totalFailed,
        List<String> failedOrderIds,
        String type
) {
    public BatchResponse() {
        this("accepted", 0, 0, List.of(), "");
    }
}
