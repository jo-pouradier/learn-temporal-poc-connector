package com.example.temporal.temporal.impl;

import com.example.temporal.model.StockBatchItem;
import com.example.temporal.temporal.activities.SendStockBatchToChannelActivity;
import com.example.temporal.temporal.worker.SendStockUpdateToChannelWorker;
import com.example.temporal.temporal.workflow.SendStockUpdateToChannelWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of long-running workflow that accumulates stock updates and sends batches.
 *
 * This workflow:
 * 1. Runs indefinitely as a singleton
 * 2. Accumulates stock updates via signals
 * 3. Triggers batch when 10 items collected OR 5 seconds elapsed
 * 4. Calls activity to send batch directly
 * 5. Resets and waits for more signals
 */
@WorkflowImpl(taskQueues = SendStockUpdateToChannelWorker.QUEUE)
public class SendStockUpdateToChannelWorkflowImpl implements SendStockUpdateToChannelWorkflow {
    private static final Logger LOG = Workflow.getLogger(SendStockUpdateToChannelWorkflowImpl.class);

    private static final int BATCH_SIZE = 10;
    private static final Duration BATCH_TIMEOUT = Duration.ofSeconds(1);
    private static final int MAX_HISTORY_ITERATIONS = 100; // Continue-as-new after 100 batches

    private final List<StockBatchItem> pendingItems = new ArrayList<>();
    private long totalBatchCounter = 0;
    private int iterationsInThisRun = 0;
    private boolean exit = false;

    private final SendStockBatchToChannelActivity stockBatchActivity =
            Workflow.newActivityStub(SendStockBatchToChannelActivity.class,
                    ActivityOptions.newBuilder()
                            .setTaskQueue(SendStockUpdateToChannelWorker.QUEUE)
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(3)
                                    .setInitialInterval(Duration.ofSeconds(3))
                                    .setBackoffCoefficient(1.5)
                                    .build()) // ASK: how this retry is handled ? re-queued ? directly after failure ?
                            .setStartToCloseTimeout(Duration.ofMinutes(2))
                            .build());

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
            Workflow.await(BATCH_TIMEOUT, () -> pendingItems.size() == BATCH_SIZE || exit);
            flushBatch();
            iterationsInThisRun++;
        }

        // 3. The "Temporal Pro" move: Continue-As-New
        // This clears history while maintaining the logical continuity of the workflow
        if (!exit) {
            SendStockUpdateToChannelWorkflow nextExecution =
                    Workflow.newContinueAsNewStub(SendStockUpdateToChannelWorkflow.class);
            nextExecution.run(totalBatchCounter);
        }
    }

    private void flushBatch() {
        List<StockBatchItem> batchToSend = new ArrayList<>(pendingItems);
        pendingItems.clear();

        totalBatchCounter++;
        String batchId = "stock-batch-" + totalBatchCounter;

        LOG.info("Flushing batch {}: size={}", batchId, batchToSend.size());

        // Activity is called synchronously here.
        // If it fails/retries, the pendingItems list for the NEXT batch
        // will continue to grow via signals in the background.
        Async.procedure(() ->stockBatchActivity.sendStockBatch(batchToSend, batchId));
    }

    @Override
    public void addStockUpdate(StockBatchItem item) {
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
