package com.example.temporal.model;

/**
 * Simple record to represent a stock job
 */
public record StockJob(String orderId, String correlationId, int stock) {
}
