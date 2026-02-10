package com.example.temporal.model;

/**
 * Represents a single stock update item to be batched.
 * Used by SendStockUpdateToChannelWorkflow to accumulate items before sending batch.
 */
public record StockBatchItem(
        String orderId,
        String correlationId,
        int stock
) {
}
