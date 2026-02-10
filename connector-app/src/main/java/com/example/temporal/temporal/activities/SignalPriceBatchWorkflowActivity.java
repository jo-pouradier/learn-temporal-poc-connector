package com.example.temporal.temporal.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for signaling the price batch workflow.
 * This activity signals the long-running batch workflow using signalWithStart pattern.
 */
@ActivityInterface
public interface SignalPriceBatchWorkflowActivity {

    /**
     * Signal the global price batching workflow to add a price update.
     * If workflow doesn't exist, it will be started. If it exists, just signals it.
     * 
     * @param orderId The order ID
     * @param correlationId The correlation ID
     * @param price The price value
     */
    @ActivityMethod
    void signalPriceBatchWorkflow(String orderId, String correlationId, int price);
}
