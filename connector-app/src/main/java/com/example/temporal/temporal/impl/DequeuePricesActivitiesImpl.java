package com.example.temporal.temporal.impl;

import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.repository.PriceQueueRepository;
import com.example.temporal.temporal.activities.DequeuePricesActivities;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.List;

@ActivityImpl
@Service
public class DequeuePricesActivitiesImpl implements DequeuePricesActivities {
    private final PriceQueueRepository priceQueueRepository;
    private final ConnectorProperties connectorProperties;

    public DequeuePricesActivitiesImpl(PriceQueueRepository priceQueueRepository,
                                       ConnectorProperties connectorProperties) {
        this.priceQueueRepository = priceQueueRepository;
        this.connectorProperties = connectorProperties;
    }

    @Override
    public List<PriceQueueRepository.PriceJob> DequeuePrices() {
        return priceQueueRepository.dequeue(connectorProperties.getBatchSize());
    }
}
