package com.example.temporal.temporal.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for signaling the stock batch workflow.
 * This activity signals the long-running batch workflow using signalWithStart pattern.
 */
@ActivityInterface
public interface SignalStockBatchWorkflowActivity {

    /**
     * Signal the global stock batching workflow to add a stock update.
     * If workflow doesn't exist, it will be started. If it exists, just signals it.
     * 
     * @param orderId The order ID
     * @param correlationId The correlation ID
     * @param stock The stock value
     */
    @ActivityMethod
    void signalStockBatchWorkflow(String orderId, String correlationId, int stock);
}
