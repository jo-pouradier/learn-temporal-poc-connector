package com.example.temporal.temporal.impl;

import com.example.temporal.model.StockBatchItem;
import com.example.temporal.temporal.activities.SignalStockBatchWorkflowActivity;
import com.example.temporal.temporal.utils.TemporalHelper;
import com.example.temporal.temporal.utils.WorkflowIdBuilder;
import com.example.temporal.temporal.worker.ProcessPriceAndStockWorker;
import com.example.temporal.temporal.workflow.SendStockUpdateToChannelWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.spring.boot.ActivityImpl;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

/**
 * Activity implementation for signaling the stock batch workflow.
 * This activity uses the signalWithStart pattern to signal the long-running batch workflow.
 * If the workflow doesn't exist, it will be started. If it exists, just signals it.
 */
@ActivityImpl
@Service
public class SignalStockBatchWorkflowActivityImpl implements SignalStockBatchWorkflowActivity {
    private static final Logger LOG = Workflow.getLogger(SignalStockBatchWorkflowActivityImpl.class);

    private final WorkflowClient workflowClient;

    public SignalStockBatchWorkflowActivityImpl(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public void signalStockBatchWorkflow(String orderId, String correlationId, int stock) {
        String workflowId = WorkflowIdBuilder.sendStockUpdatesWorkflowId();
        StockBatchItem item = new StockBatchItem(orderId, correlationId, stock);

        try {
            var workflow = TemporalHelper.createWorkflowStub(
                    workflowClient, 
                    SendStockUpdateToChannelWorkflow.class, 
                    ProcessPriceAndStockWorker.QUEUE,
                    workflowId
            );
            
            var request = workflowClient.newSignalWithStartRequest();
            request.add(workflow::run, 0L);
            request.add(workflow::addStockUpdate, item);
            workflowClient.signalWithStart(request);

            LOG.info("Signaled stock batch workflow: workflowId={}, orderId={}, correlationId={}", 
                    workflowId, orderId, correlationId);
        } catch (Exception e) {
            LOG.error("Failed to signal stock batch workflow: workflowId={}, orderId={}, correlationId={}, error={}", 
                    workflowId, orderId, correlationId, e.getMessage(), e);
            throw new RuntimeException("Failed to signal stock batch workflow", e);
        }
    }
}
