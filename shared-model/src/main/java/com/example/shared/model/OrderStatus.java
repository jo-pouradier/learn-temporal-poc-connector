package com.example.shared.model;

public enum OrderStatus {
    // Initial states
    PENDING,
    ACCEPTED,
    
    // Main-app states
    SENT_TO_CONNECTOR,
    
    // Connector-app states
    QUEUED,
    PROCESSING_PRICE,
    PROCESSING_STOCK,
    
    // Completion states
    COMPLETED,
    PARTIAL,
    FAILED,
    ERROR
}
