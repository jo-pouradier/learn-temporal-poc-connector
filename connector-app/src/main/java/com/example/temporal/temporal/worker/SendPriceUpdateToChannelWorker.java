package com.example.temporal.temporal.worker;

import com.example.temporal.temporal.impl.SendPriceBatchToChannelActivityImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.stereotype.Service;

/**
 * Worker for the long-running SendPriceUpdateToChannelWorkflow.
 * This workflow accumulates price updates and sends them in batches.
 */
@Service
public class SendPriceUpdateToChannelWorker {
    public static final String QUEUE = "send-price-update-to-channel";

    public SendPriceUpdateToChannelWorker(WorkerFactory factory, 
                                          SendPriceBatchToChannelActivityImpl priceBatchActivity) {
        Worker worker = factory.newWorker(QUEUE);
        worker.registerActivitiesImplementations(priceBatchActivity);
    }
}
