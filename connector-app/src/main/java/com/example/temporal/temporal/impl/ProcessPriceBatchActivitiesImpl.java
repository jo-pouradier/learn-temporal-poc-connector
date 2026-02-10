package com.example.temporal.temporal.impl;

import com.example.shared.model.PriceRequest;
import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.model.PriceJob;
import com.example.temporal.service.RequestService;
import com.example.temporal.temporal.activities.ProcessPriceBatchActivities;
import io.temporal.spring.boot.ActivityImpl;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

@ActivityImpl
@Service
public class ProcessPriceBatchActivitiesImpl implements ProcessPriceBatchActivities {
    private static final Logger LOG = Workflow.getLogger(ProcessPriceBatchActivitiesImpl.class);

    private final RequestService requestService;
    private final ConnectorProperties properties;

    public ProcessPriceBatchActivitiesImpl(RequestService requestService, ConnectorProperties properties) {
        this.requestService = requestService;
        this.properties = properties;
    }

    @Override
    public void processPriceRequest(PriceJob job) {
        LOG.info("Sending price job to channel: orderId={}, correlationId={}, price={}",
                job.orderId(), job.correlationId(), job.price());
        requestService.processJobRequest(job.orderId(), job.correlationId(), "price", properties.getChannelPriceUrl(), new PriceRequest(job.orderId(), job.price()));
    }


}
