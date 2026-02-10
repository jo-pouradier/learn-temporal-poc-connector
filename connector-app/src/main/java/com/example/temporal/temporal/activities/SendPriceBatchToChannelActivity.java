package com.example.temporal.temporal.activities;

import com.example.temporal.model.PriceBatchItem;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

/**
 * Activity interface for sending batched price updates to channel-app.
 */
@ActivityInterface
public interface SendPriceBatchToChannelActivity {

    @ActivityMethod
    void sendPriceBatch(List<PriceBatchItem> items, String batchCorrelationId);
}
