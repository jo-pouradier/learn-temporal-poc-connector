package com.example.temporal.temporal.activities;

import com.example.temporal.repository.StockQueueRepository;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface DequeueStocksActivities {

    @ActivityMethod
    List<StockQueueRepository.StockJob> DequeueStocks();
}
