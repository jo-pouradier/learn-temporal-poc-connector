package com.example.temporal.temporal.impl;

import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.repository.StockQueueRepository;
import com.example.temporal.temporal.activities.DequeueStocksActivities;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@ActivityImpl
@Service
public class DequeueStocksActivitiesImpl implements DequeueStocksActivities {
    private final StockQueueRepository stockQueueRepository;
    private final ConnectorProperties connectorProperties;

    public DequeueStocksActivitiesImpl(StockQueueRepository stockQueueRepository,
                                       ConnectorProperties connectorProperties) {
        this.stockQueueRepository = stockQueueRepository;
        this.connectorProperties = connectorProperties;
    }

    @Override
    public List<StockQueueRepository.StockJob> DequeueStocks() {
        return stockQueueRepository.dequeue(connectorProperties.getBatchSize());
    }
}
