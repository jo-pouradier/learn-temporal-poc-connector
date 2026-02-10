package com.example.temporal.temporal.utils;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

public class TemporalHelper {

    public static final String DEFAULT_CUSTOMER_ID = "customer-123";
    public static final String DEFAULT_ORDER_ID = "order-789";
    public static final String DEFAULT_RRN_ID = "RRN-123";

    //    public static OrderProcessingRequest defaultOrderProcessingRequest() {
//        return new OrderProcessingRequest(DEFAULT_CUSTOMER_ID, DEFAULT_ORDER_ID);
//    }
//
//    public static PaymentProcessedEvent defaultPaymentProcessedEvent() {
//        return new PaymentProcessedEvent(DEFAULT_CUSTOMER_ID, DEFAULT_RRN_ID, 1000, new TreeMap<>());
//    }
//
//    public static OrderProcessingWorkflow createWorkflowStubWithDefaults(WorkflowClient client) {
//        return createWorkflowStub(client, DEFAULT_CUSTOMER_ID, DEFAULT_ORDER_ID);
//    }
//
    public static <T> T createWorkflowStub(WorkflowClient client, Class<T> clazz, String queue, String workflowId) {
        return client.newWorkflowStub(
                clazz,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(queue)
                        .setWorkflowId(workflowId)
                        .build());
    }
}
