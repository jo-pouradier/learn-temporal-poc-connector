package com.example.temporal.temporal.impl;

import com.example.shared.model.PriceRequest;
import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.repository.PriceQueueRepository;
import com.example.temporal.service.RequestService;
import com.example.temporal.temporal.activities.ProcessPriceBatchActivities;
import io.temporal.spring.boot.ActivityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@ActivityImpl
@Service
public class ProcessPriceBatchActivitiesImpl implements ProcessPriceBatchActivities {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessPriceBatchActivitiesImpl.class);
    private static final String PRICE_QUEUE = "connector_price";

    private final RequestService requestService;
    private final ConnectorProperties properties;

    public ProcessPriceBatchActivitiesImpl(RequestService requestService, ConnectorProperties properties) {
        this.requestService = requestService;
        this.properties = properties;
    }

    @Override
    public void processPriceBatch(List<PriceQueueRepository.PriceJob> batch) {
        for (PriceQueueRepository.PriceJob job : batch) {
            LOG.info("Sending price to channel: orderId={}, correlationId={}, price={}",
                    job.orderId(), job.correlationId(), job.price());
            requestService.processJob(job.orderId(), job.correlationId(), "price", properties.getChannelPriceUrl(), new PriceRequest(job.orderId(), job.price()));
        }
    }
}
