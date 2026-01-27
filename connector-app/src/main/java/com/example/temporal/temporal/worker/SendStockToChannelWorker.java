package com.example.temporal.temporal.worker;

import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.repository.StockQueueRepository;
import com.example.temporal.service.RequestService;
import com.example.temporal.temporal.impl.DequeueStocksActivitiesImpl;
import com.example.temporal.temporal.impl.ProcessStockBatchActivitiesImpl;
import com.example.temporal.temporal.impl.SendStockToChannelWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.stereotype.Service;

@Service
public class SendStockToChannelWorker {
    private final String queue = "send-stock-to-channel";

    SendStockToChannelWorker(WorkerFactory factory, StockQueueRepository stockQueueRepository, RequestService requestService, ConnectorProperties connectorProperties) {
        Worker worker = factory.newWorker(queue);
        worker.registerWorkflowImplementationTypes(SendStockToChannelWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                new DequeueStocksActivitiesImpl(stockQueueRepository, connectorProperties),
                new ProcessStockBatchActivitiesImpl(requestService, connectorProperties)
        );
    }
}
