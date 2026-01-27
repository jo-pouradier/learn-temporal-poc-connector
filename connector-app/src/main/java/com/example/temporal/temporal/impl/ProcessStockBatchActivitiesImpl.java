package com.example.temporal.temporal.impl;

import com.example.shared.model.StockRequest;
import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.repository.StockQueueRepository;
import com.example.temporal.service.RequestService;
import com.example.temporal.temporal.activities.ProcessStockBatchActivities;
import io.temporal.spring.boot.ActivityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@ActivityImpl
@Service
public class ProcessStockBatchActivitiesImpl implements ProcessStockBatchActivities {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessStockBatchActivitiesImpl.class);
    private static final String STOCK_QUEUE = "connector_stock";

    private final RequestService requestService;
    private final ConnectorProperties properties;

    public ProcessStockBatchActivitiesImpl(RequestService requestService, ConnectorProperties properties) {
        this.requestService = requestService;
        this.properties = properties;
    }

    @Override
    public void processStockBatch(List<StockQueueRepository.StockJob> batch) {
        for (StockQueueRepository.StockJob job : batch) {
            LOG.info("Sending stock to channel: orderId={}, correlationId={}, stock={}",
                    job.orderId(), job.correlationId(), job.stock());
            requestService.processJob(job.orderId(), job.correlationId(), "stock", properties.getChannelStockUrl(), new StockRequest(job.orderId(), job.stock()));
        }
    }
}
