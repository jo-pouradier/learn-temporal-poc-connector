package com.example.temporal.temporal.impl;

import com.example.temporal.temporal.activities.DequeuePricesActivities;
import com.example.temporal.temporal.activities.ProcessPriceBatchActivities;
import com.example.temporal.temporal.workflow.SendPriceToChannelWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

@WorkflowImpl(taskQueues = "send-price-to-channel")
public class SendPriceToChannelWorkflowImpl implements SendPriceToChannelWorkflow {

    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(1))
            .build();

    private final ProcessPriceBatchActivities processPriceBatchActivities =
            Workflow.newActivityStub(ProcessPriceBatchActivities.class, ACTIVITY_OPTIONS);

    private final DequeuePricesActivities dequeuePricesActivities =
            Workflow.newActivityStub(DequeuePricesActivities.class, ACTIVITY_OPTIONS);


    @Override
    public void processPriceBatch() {
        var batch = dequeuePricesActivities.DequeuePrices();

        processPriceBatchActivities.processPriceBatch(batch);
    }
}
