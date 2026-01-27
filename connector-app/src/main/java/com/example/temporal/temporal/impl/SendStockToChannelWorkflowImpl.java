package com.example.temporal.temporal.impl;

import com.example.temporal.temporal.activities.DequeueStocksActivities;
import com.example.temporal.temporal.activities.ProcessStockBatchActivities;
import com.example.temporal.temporal.workflow.SendStockToChannelWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

@WorkflowImpl(taskQueues = "send-stock-to-channel")
public class SendStockToChannelWorkflowImpl implements SendStockToChannelWorkflow {

    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(1))
            .build();

    private final ProcessStockBatchActivities processStockBatchActivities =
            Workflow.newActivityStub(ProcessStockBatchActivities.class, ACTIVITY_OPTIONS);

    private final DequeueStocksActivities dequeueStocksActivities =
            Workflow.newActivityStub(DequeueStocksActivities.class, ACTIVITY_OPTIONS);


    @Override
    public void processStockBatch() {
        var batch = dequeueStocksActivities.DequeueStocks();

        processStockBatchActivities.processStockBatch(batch);
    }
}
