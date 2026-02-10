package com.example.temporal.temporal.worker;

import com.example.temporal.temporal.impl.*;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProcessPriceAndStockWorker {
    public static final String QUEUE = "process-price-and-stock";

    ProcessPriceAndStockWorker(WorkerFactory factory,
                               ProcessPriceBatchActivitiesImpl priceActivities,
                               ProcessStockBatchActivitiesImpl stockActivities,
                               SendFinalCallbackActivityImpl callbackActivity,
                               SignalPriceBatchWorkflowActivityImpl signalPriceActivity,
                               SignalStockBatchWorkflowActivityImpl signalStockActivity) {
        Worker worker = factory.newWorker(QUEUE);

        worker.registerWorkflowImplementationTypes(ProcessPriceAndStockWorkflowImpl.class,
                SendPriceUpdateToChannelWorkflowImpl.class,
                SendStockUpdateToChannelWorkflowImpl.class
        );

        worker.registerActivitiesImplementations(
                priceActivities,
                stockActivities,
                callbackActivity,
                signalPriceActivity,
                signalStockActivity
        );
    }
}
