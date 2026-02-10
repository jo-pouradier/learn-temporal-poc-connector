package com.example.temporal.model;

/**
 * Represents a single price update item to be batched.
 * Used by SendPriceUpdateToChannelWorkflow to accumulate items before sending batch.
 */
public record PriceBatchItem(
        String orderId,
        String correlationId,
        int price
) {
}
