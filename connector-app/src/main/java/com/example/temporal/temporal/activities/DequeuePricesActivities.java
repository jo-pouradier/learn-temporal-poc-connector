package com.example.temporal.temporal.activities;

import com.example.temporal.repository.PriceQueueRepository;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface DequeuePricesActivities {

    @ActivityMethod
    List<PriceQueueRepository.PriceJob> DequeuePrices();
}
