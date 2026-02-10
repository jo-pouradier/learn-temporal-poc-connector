package com.example.temporal.service;

import com.example.temporal.temporal.utils.WorkflowIdBuilder;
import com.example.temporal.temporal.workflow.SendPriceUpdateToChannelWorkflow;
import com.example.temporal.temporal.workflow.SendStockUpdateToChannelWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for monitoring batch workflow health and status.
 */
@Service
public class BatchWorkflowHealthService {

    private static final Logger LOG = LoggerFactory.getLogger(BatchWorkflowHealthService.class);

    private final WorkflowClient workflowClient;

    public BatchWorkflowHealthService(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /**
     * Gets the health status of all batch workflows.
     *
     * @return health status map with workflow details
     */
    public Map<String, Object> getBatchWorkflowsHealth() {
        Map<String, Object> health = new HashMap<>();
        
        health.put("priceWorkflow", getPriceWorkflowHealth());
        health.put("stockWorkflow", getStockWorkflowHealth());
        
        boolean allHealthy = (boolean) ((Map<?, ?>) health.get("priceWorkflow")).get("running") &&
                            (boolean) ((Map<?, ?>) health.get("stockWorkflow")).get("running");
        
        health.put("status", allHealthy ? "UP" : "DOWN");
        
        return health;
    }

    /**
     * Gets the health status of the price batch workflow.
     *
     * @return health status map
     */
    private Map<String, Object> getPriceWorkflowHealth() {
        String workflowId = WorkflowIdBuilder.sendPriceUpdatesWorkflowId();
        Map<String, Object> status = new HashMap<>();
        
        try {
            SendPriceUpdateToChannelWorkflow workflow = workflowClient.newWorkflowStub(
                    SendPriceUpdateToChannelWorkflow.class, workflowId);
            
            // Query workflow for pending count and batch counter
            int pendingCount = workflow.getPendingCount();
            long batchCounter = workflow.getBatchCounter();
            
            status.put("workflowId", workflowId);
            status.put("running", true);
            status.put("pendingCount", pendingCount);
            status.put("batchCounter", batchCounter);
            status.put("totalProcessed", batchCounter * 10); // Approximate, assuming batch size of 10
            
            LOG.debug("Price workflow health: running=true, pendingCount={}, batchCounter={}",
                    pendingCount, batchCounter);
            
        } catch (Exception e) {
            status.put("workflowId", workflowId);
            status.put("running", false);
            status.put("error", e.getMessage());
            
            LOG.warn("Price batch workflow not running: workflowId={}, error={}",
                    workflowId, e.getMessage());
        }
        
        return status;
    }

    /**
     * Gets the health status of the stock batch workflow.
     *
     * @return health status map
     */
    private Map<String, Object> getStockWorkflowHealth() {
        String workflowId = WorkflowIdBuilder.sendStockUpdatesWorkflowId();
        Map<String, Object> status = new HashMap<>();
        
        try {
            SendStockUpdateToChannelWorkflow workflow = workflowClient.newWorkflowStub(
                    SendStockUpdateToChannelWorkflow.class, workflowId);
            
            // Query workflow for pending count and batch counter
            int pendingCount = workflow.getPendingCount();
            long batchCounter = workflow.getBatchCounter();
            
            status.put("workflowId", workflowId);
            status.put("running", true);
            status.put("pendingCount", pendingCount);
            status.put("batchCounter", batchCounter);
            status.put("totalProcessed", batchCounter * 10); // Approximate, assuming batch size of 10
            
            LOG.debug("Stock workflow health: running=true, pendingCount={}, batchCounter={}",
                    pendingCount, batchCounter);
            
        } catch (Exception e) {
            status.put("workflowId", workflowId);
            status.put("running", false);
            status.put("error", e.getMessage());
            
            LOG.warn("Stock batch workflow not running: workflowId={}, error={}",
                    workflowId, e.getMessage());
        }
        
        return status;
    }

    /**
     * Checks if a specific workflow is running.
     *
     * @param workflowId the workflow ID to check
     * @return true if workflow is running, false otherwise
     */
    public boolean isWorkflowRunning(String workflowId) {
        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowId);
            stub.getExecution();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
