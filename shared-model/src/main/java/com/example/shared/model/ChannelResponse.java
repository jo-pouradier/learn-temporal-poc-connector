package com.example.shared.model;

public record ChannelResponse(String status, String type) {

    public ChannelResponse() {
        this(null, null);
    }
}
