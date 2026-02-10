package com.example.temporal.temporal.workflow;

import com.example.temporal.model.StockBatchItem;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Long-running workflow that accumulates stock updates and sends them in batches.
 * This is a singleton workflow (single instance handles all stock updates globally).
 * 
 * Batching Strategy:
 * - Accumulates items via signals
 * - Triggers batch when: 10 items accumulated OR 5 seconds elapsed since first item
 * - Spawns child workflow to send batch
 * - Continues running indefinitely
 */
@WorkflowInterface
public interface SendStockUpdateToChannelWorkflow {

    /**
     * Main workflow method. Runs indefinitely, waiting for signals and triggering batches.
     */
    @WorkflowMethod
    void run(long startBatch);

    /**
     * Signal method to add a stock update to the batch.
     * @param item Stock update to add to batch
     */
    @SignalMethod
    void addStockUpdate(StockBatchItem item);

    @SignalMethod
    void stopWorkflow();

    /**
     * Query the current number of pending items in the batch.
     * @return Number of items waiting to be sent
     */
    @QueryMethod
    int getPendingCount();

    /**
     * Query the total number of batches sent so far.
     * @return Total batch counter
     */
    @QueryMethod
    long getBatchCounter();
}
