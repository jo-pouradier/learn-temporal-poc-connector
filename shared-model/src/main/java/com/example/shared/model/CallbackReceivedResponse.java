package com.example.shared.model;

public record CallbackReceivedResponse(String status) {

    public CallbackReceivedResponse() {
        this(null);
    }
}
