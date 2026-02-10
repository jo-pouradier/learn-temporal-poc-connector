package com.example.temporal.temporal.activities;

import com.example.temporal.model.StockJob;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ProcessStockBatchActivities {

    @ActivityMethod
    void processStockRequest(StockJob job);

}
