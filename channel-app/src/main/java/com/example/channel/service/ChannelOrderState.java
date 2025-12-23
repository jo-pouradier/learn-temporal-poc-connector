package com.example.channel.service;

import java.time.LocalDateTime;

public record ChannelOrderState(
    Integer price, 
    Integer stock, 
    String priceStatus,  // "pending", "processing", "completed", "failed"
    String stockStatus,  // "pending", "processing", "completed", "failed"
    LocalDateTime lastUpdatedAt
) {
}
