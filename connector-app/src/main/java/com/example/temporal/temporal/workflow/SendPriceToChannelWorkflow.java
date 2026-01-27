package com.example.temporal.temporal.workflow;

import com.example.temporal.repository.PriceQueueRepository;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

@WorkflowInterface
public interface SendPriceToChannelWorkflow {
    @WorkflowMethod
    void processPriceBatch();
}
