package com.example.shared.model;

import java.time.LocalDateTime;

public record StateEvent(
    LocalDateTime timestamp,
    EventType eventType,
    OrderStatus status,
    String details
) {
    /**
     * Constructor without details
     */
    public StateEvent(EventType eventType, OrderStatus status) {
        this(LocalDateTime.now(), eventType, status, null);
    }
    
    /**
     * Constructor with details, auto-timestamp
     */
    public StateEvent(EventType eventType, OrderStatus status, String details) {
        this(LocalDateTime.now(), eventType, status, details);
    }
}
