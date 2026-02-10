package com.example.temporal.temporal.impl;

import com.example.temporal.model.PriceBatchItem;
import com.example.temporal.temporal.activities.SendPriceBatchToChannelActivity;
import com.example.temporal.temporal.worker.SendPriceUpdateToChannelWorker;
import com.example.temporal.temporal.workflow.SendPriceUpdateToChannelWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of long-running workflow that accumulates price updates and sends batches.
 *
 * This workflow:
 * 1. Runs indefinitely as a singleton
 * 2. Accumulates price updates via signals
 * 3. Triggers batch when 10 items collected OR 5 seconds elapsed
 * 4. Calls activity to send batch directly
 * 5. Resets and waits for more signals
 */
@WorkflowImpl(taskQueues = SendPriceUpdateToChannelWorker.QUEUE)
public class SendPriceUpdateToChannelWorkflowImpl implements SendPriceUpdateToChannelWorkflow {
    private static final Logger LOG = Workflow.getLogger(SendPriceUpdateToChannelWorkflowImpl.class);

    private static final int BATCH_SIZE = 10;
    private static final Duration BATCH_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_HISTORY_ITERATIONS = 100; // Continue-as-new after 100 batches

    private final List<PriceBatchItem> pendingItems = new ArrayList<>();
    private long totalBatchCounter = 0;
    private int iterationsInThisRun = 0;
    private boolean exit = false;

    private final SendPriceBatchToChannelActivity priceBatchActivity =
            Workflow.newActivityStub(SendPriceBatchToChannelActivity.class,
                    ActivityOptions.newBuilder()
                            .setTaskQueue(SendPriceUpdateToChannelWorker.QUEUE)
                            .setStartToCloseTimeout(Duration.ofMinutes(2)).build());

    @Override
    public void run(long startingBatchCount) {
        this.totalBatchCounter = startingBatchCount;

        // Main processing loop for this execution "generation"
        while (iterationsInThisRun < MAX_HISTORY_ITERATIONS && !exit) {

            // 1. Wait for the first item to arrive
            Workflow.await(() -> !pendingItems.isEmpty() || exit);

            if (exit) break;

            // 2. Wait for Batch Size OR Batch Timeout
            // This is the idiomatic way to handle "Wait up to X seconds for Y to happen"
            Workflow.await(BATCH_TIMEOUT, () -> pendingItems.size() >= BATCH_SIZE || exit);

            if (!pendingItems.isEmpty()) {
                flushBatch();
                iterationsInThisRun++;
            }
        }

        // 3. The "Temporal Pro" move: Continue-As-New
        // This clears history while maintaining the logical continuity of the workflow
        if (!exit) {
            SendPriceUpdateToChannelWorkflow nextExecution =
                    Workflow.newContinueAsNewStub(SendPriceUpdateToChannelWorkflow.class);
            nextExecution.run(totalBatchCounter);
        }
    }

    private void flushBatch() {
        List<PriceBatchItem> batchToSend = new ArrayList<>(pendingItems);
        pendingItems.clear();

        totalBatchCounter++;
        String batchId = "price-batch-" + totalBatchCounter;

        LOG.info("Flushing batch {}: size={}", batchId, batchToSend.size());

        // Activity is called synchronously here.
        // If it fails/retries, the pendingItems list for the NEXT batch
        // will continue to grow via signals in the background.
        priceBatchActivity.sendPriceBatch(batchToSend, batchId);
    }

    @Override
    public void addPriceUpdate(PriceBatchItem item) {
        // Signals are processed even while Workflow.await or Activities are running
        pendingItems.add(item);
    }

    @Override
    public void stopWorkflow() {
        this.exit = true;
    }

    @Override
    public int getPendingCount() { return pendingItems.size(); }

    @Override
    public long getBatchCounter() {
        return totalBatchCounter;
    }
}
