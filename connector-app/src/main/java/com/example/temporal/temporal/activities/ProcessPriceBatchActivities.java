package com.example.temporal.temporal.activities;

import com.example.temporal.model.PriceJob;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ProcessPriceBatchActivities {

    @ActivityMethod
    void processPriceRequest(PriceJob job);
}
