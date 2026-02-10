package com.example.temporal.temporal.impl;

import com.example.shared.model.StockRequest;
import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.model.StockJob;
import com.example.temporal.service.RequestService;
import com.example.temporal.temporal.activities.ProcessStockBatchActivities;
import io.temporal.spring.boot.ActivityImpl;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

@ActivityImpl
@Service
public class ProcessStockBatchActivitiesImpl implements ProcessStockBatchActivities {
    private static final Logger LOG = Workflow.getLogger(ProcessStockBatchActivitiesImpl.class);

    private final RequestService requestService;
    private final ConnectorProperties properties;

    public ProcessStockBatchActivitiesImpl(RequestService requestService, ConnectorProperties properties) {
        this.requestService = requestService;
        this.properties = properties;
    }

    @Override
    public void processStockRequest(StockJob job) {
        LOG.info("Sending job stock to channel: orderId={}, correlationId={}, stock={}",
                job.orderId(), job.correlationId(), job.stock());
        requestService.processJobRequest(job.orderId(), job.correlationId(), "stock", properties.getChannelStockUrl(), new StockRequest(job.orderId(), job.stock()));
    }
}
