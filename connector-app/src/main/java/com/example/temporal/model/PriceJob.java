package com.example.temporal.model;

/**
 * Simple record to represent a price job
 */
public record PriceJob(String orderId, String correlationId, int price) {
}
