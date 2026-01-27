package com.example.temporal.temporal.worker;

import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.repository.PriceQueueRepository;
import com.example.temporal.service.RequestService;
import com.example.temporal.temporal.impl.DequeuePricesActivitiesImpl;
import com.example.temporal.temporal.impl.ProcessPriceBatchActivitiesImpl;
import com.example.temporal.temporal.impl.SendPriceToChannelWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.stereotype.Service;

@Service
public class SendPriceToChannelWorker {
    private final String queue = "send-price-to-channel";

    SendPriceToChannelWorker(WorkerFactory factory,
                             PriceQueueRepository priceQueueRepository,
                             RequestService requestService,
                             ConnectorProperties connectorProperties) {
        Worker worker = factory.newWorker(queue);
        worker.registerWorkflowImplementationTypes(SendPriceToChannelWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                new DequeuePricesActivitiesImpl(priceQueueRepository, connectorProperties),
                new ProcessPriceBatchActivitiesImpl(requestService, connectorProperties)
        );
    }
}
