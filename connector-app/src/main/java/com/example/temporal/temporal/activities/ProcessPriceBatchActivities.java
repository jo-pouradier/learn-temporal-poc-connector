package com.example.temporal.temporal.activities;

import com.example.temporal.repository.PriceQueueRepository;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface ProcessPriceBatchActivities {
    @ActivityMethod
    void processPriceBatch(List<PriceQueueRepository.PriceJob> batch);

}
