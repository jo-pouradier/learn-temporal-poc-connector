package com.example.shared.model;

public enum EventType {
    // Request lifecycle events
    REQUEST_RECEIVED,
    REQUEST_ACCEPTED,
    REQUEST_SUBMITTED,
    
    // Queue events
    BATCH_QUEUED,
    BATCH_SENT,
    QUEUE_FULL,
    
    // Processing events
    PROCESSING_STARTED,
    JOB_SENT_TO_CHANNEL,
    
    // Status changes
    STATUS_CHANGE,
    
    // Callback events
    CALLBACK_RECEIVED,
    CALLBACK_SENT,
    
    // Error events
    ERROR,
    VALIDATION_ERROR,
    NETWORK_ERROR
}
