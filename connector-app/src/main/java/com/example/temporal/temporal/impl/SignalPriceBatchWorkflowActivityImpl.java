package com.example.temporal.temporal.impl;

import com.example.temporal.model.PriceBatchItem;
import com.example.temporal.temporal.activities.SignalPriceBatchWorkflowActivity;
import com.example.temporal.temporal.utils.TemporalHelper;
import com.example.temporal.temporal.utils.WorkflowIdBuilder;
import com.example.temporal.temporal.worker.ProcessPriceAndStockWorker;
import com.example.temporal.temporal.workflow.SendPriceUpdateToChannelWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.spring.boot.ActivityImpl;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

/**
 * Activity implementation for signaling the price batch workflow.
 * This activity uses the signalWithStart pattern to signal the long-running batch workflow.
 * If the workflow doesn't exist, it will be started. If it exists, just signals it.
 */
@ActivityImpl
@Service
public class SignalPriceBatchWorkflowActivityImpl implements SignalPriceBatchWorkflowActivity {
    private static final Logger LOG = Workflow.getLogger(SignalPriceBatchWorkflowActivityImpl.class);

    private final WorkflowClient workflowClient;

    public SignalPriceBatchWorkflowActivityImpl(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public void signalPriceBatchWorkflow(String orderId, String correlationId, int price) {
        String workflowId = WorkflowIdBuilder.sendPriceUpdatesWorkflowId();
        PriceBatchItem item = new PriceBatchItem(orderId, correlationId, price);

        try {
            var workflow = TemporalHelper.createWorkflowStub(
                    workflowClient, 
                    SendPriceUpdateToChannelWorkflow.class, 
                    ProcessPriceAndStockWorker.QUEUE,
                    workflowId
            );
            
            var request = workflowClient.newSignalWithStartRequest();
            request.add(workflow::run, 0L);
            request.add(workflow::addPriceUpdate, item);
            workflowClient.signalWithStart(request);

            LOG.info("Signaled price batch workflow: workflowId={}, orderId={}, correlationId={}", 
                    workflowId, orderId, correlationId);
        } catch (Exception e) {
            LOG.error("Failed to signal price batch workflow: workflowId={}, orderId={}, correlationId={}, error={}", 
                    workflowId, orderId, correlationId, e.getMessage(), e);
            throw new RuntimeException("Failed to signal price batch workflow", e);
        }
    }
}
