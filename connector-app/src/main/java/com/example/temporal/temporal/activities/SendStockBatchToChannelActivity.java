package com.example.temporal.temporal.activities;

import com.example.temporal.model.StockBatchItem;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

/**
 * Activity interface for sending batched stock updates to channel-app.
 */
@ActivityInterface
public interface SendStockBatchToChannelActivity {

    @ActivityMethod
    void sendStockBatch(List<StockBatchItem> items, String batchCorrelationId);
}
