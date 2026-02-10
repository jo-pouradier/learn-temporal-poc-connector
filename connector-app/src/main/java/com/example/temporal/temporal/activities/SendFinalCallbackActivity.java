package com.example.temporal.temporal.activities;

import com.example.shared.model.CallbackResponse;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SendFinalCallbackActivity {
    @ActivityMethod
    void sendFinalCallback(CallbackResponse priceCallback, 
                          CallbackResponse stockCallback, 
                          String orderId, 
                          String correlationId);
}
