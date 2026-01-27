package com.example.temporal.temporal.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface SendStockToChannelWorkflow {
    @WorkflowMethod
    void processStockBatch();
}
