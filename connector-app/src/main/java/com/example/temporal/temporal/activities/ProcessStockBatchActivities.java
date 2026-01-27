package com.example.temporal.temporal.activities;

import com.example.temporal.repository.StockQueueRepository;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface ProcessStockBatchActivities {
    @ActivityMethod
    void processStockBatch(List<StockQueueRepository.StockJob> batch);

}
