package com.example.temporal.temporal;

import com.example.temporal.temporal.impl.DequeuePricesActivitiesImpl;
import com.example.temporal.temporal.workflow.SendPriceToChannelWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfiguration {

//    @Bean
//    public WorkflowServiceStubs workflowServiceStubs() {
//        return WorkflowServiceStubs.newLocalServiceStubs();
////        Worker worker = factory.newWorker("send-prices-to-channel");
////        worker.registerWorkflowImplementationTypes(SendPriceToChannelWorkflow.class) ;
////        worker.registerActivitiesImplementations(new DequeuePricesActivitiesImpl());
//
//    }
//
//    @Bean
//    public WorkflowClient workflowClient(WorkflowServiceStubs service) {
//        return WorkflowClient.newInstance(service);
//    }

    @Bean
    public WorkerFactory factory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

}
