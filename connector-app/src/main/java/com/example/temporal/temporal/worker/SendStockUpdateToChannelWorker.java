package com.example.temporal.temporal.worker;

import com.example.temporal.temporal.impl.SendStockBatchToChannelActivityImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import org.springframework.stereotype.Service;

/**
 * Worker for the long-running SendStockUpdateToChannelWorkflow.
 * This workflow accumulates stock updates and sends them in batches.
 */
@Service
public class SendStockUpdateToChannelWorker {
    public static final String QUEUE = "send-stock-update-to-channel";

    public SendStockUpdateToChannelWorker(WorkerFactory factory,
                                          SendStockBatchToChannelActivityImpl stockBatchActivity) {
        Worker worker = factory.newWorker(
                QUEUE,
                WorkerOptions.newBuilder()
                        .setMaxTaskQueueActivitiesPerSecond(0.4) // rate limit of 0.5req/s == 1req/2s
                        .setUsingVirtualThreads(true) // omg
                        .build());
        worker.registerActivitiesImplementations(stockBatchActivity);
    }
}