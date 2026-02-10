package com.example.temporal.temporal.impl;

import com.example.shared.model.CallbackResponse;
import com.example.shared.model.OrderStatus;
import com.example.shared.model.PriceAndStockRequest;
import com.example.shared.model.RequestState;
import com.example.temporal.temporal.activities.SendFinalCallbackActivity;
import com.example.temporal.temporal.activities.SignalPriceBatchWorkflowActivity;
import com.example.temporal.temporal.activities.SignalStockBatchWorkflowActivity;
import com.example.temporal.temporal.workflow.ProcessPriceAndStockWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.time.Duration;

public class ProcessPriceAndStockWorkflowImpl implements ProcessPriceAndStockWorkflow {
    private static final Logger LOG = Workflow.getLogger(ProcessPriceAndStockWorkflowImpl.class);

    private PriceAndStockRequest data;
    private RequestState state;
    private String correlationId;

    // Retry policy for final callback activity
    private static final ActivityOptions CALLBACK_ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setMaximumInterval(Duration.ofSeconds(30))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(5)
                    .build())
            .build();

    // Activity options for signaling batch workflows
    private static final ActivityOptions SIGNAL_ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setMaximumInterval(Duration.ofSeconds(10))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(3)
                    .build())
            .build();

    /**
     * Initialize workflow state when the workflow instance is created.
     * This ensures state is available before any signals arrive or the workflow method runs.
     */
    @WorkflowInit
    public ProcessPriceAndStockWorkflowImpl(PriceAndStockRequest request, String correlationId) {
        this.correlationId = correlationId;
        this.data = request;
        this.state = new RequestState(correlationId, request);
    }

    @Override
    public void processPriceAndStock(PriceAndStockRequest request, String correlationId) {
        LOG.info("Starting workflow: orderId={}, correlationId={}", request.orderId(), correlationId);

        // Signal the long-running batch workflows (signalWithStart pattern)
        // These workflows accumulate updates and send them in batches
        signalPriceBatchWorkflow(request.orderId(), correlationId, request.price());
        signalStockBatchWorkflow(request.orderId(), correlationId, request.stock());

        state.setStatus(OrderStatus.PROCESSING_PRICE);

        LOG.info("Waiting for callbacks: orderId={}, correlationId={}", request.orderId(), correlationId);


        // Wait for both callbacks via signals
        boolean signalsReceived = Workflow.await(
                Duration.ofSeconds(30),
                () -> state.isPriceCompleted() && state.isStockCompleted()
        );
        if (!signalsReceived) {
            throw ApplicationFailure.newFailure(
                    "Timeout waiting for price and stock callbacks after 30 seconds",
                    "SignalTimeoutError"
            );
        }

        LOG.info("Both callbacks received, sending final callback: orderId={}, correlationId={}",
                request.orderId(), correlationId);

        // Send final combined callback (activity will determine final status)
        Workflow.newActivityStub(SendFinalCallbackActivity.class, CALLBACK_ACTIVITY_OPTIONS)
        .sendFinalCallback(
                state.getPriceCallback(),
                state.getStockCallback(),
                data.orderId(),
                correlationId
        );

        // Determine final status based on callback results
        OrderStatus finalStatus = determineFinalStatus(state.getPriceCallback(), state.getStockCallback());
        state.setStatus(finalStatus);

        LOG.info("Workflow completed: orderId={}, correlationId={}, finalStatus={}",
                request.orderId(), correlationId, finalStatus);
    }

    @Override
    public RequestState getState() {
        return state;
    }

    @SignalMethod
    public void setState(OrderStatus status) {
        state.setStatus(status);
    }

    @Override
    public void setPriceResponse(CallbackResponse callbackResponse) {
        state.setPriceCallback(callbackResponse);
        state.setPriceCompleted(true);
        LOG.info("Price callback received in workflow: orderId={}, isPriceOk={}",
                data.orderId(), callbackResponse.isPriceOk());
    }

    @Override
    public void setStockResponse(CallbackResponse callbackResponse) {
        state.setStockCallback(callbackResponse);
        state.setStockCompleted(true);
        LOG.info("Stock callback received in workflow: orderId={}, isStockOk={}",
                data.orderId(), callbackResponse.isStockOk());
    }

    /**
     * Signal the global price batching workflow using an activity.
     * The activity will use the signalWithStart pattern to signal the workflow.
     * If workflow doesn't exist, start it. If it exists, just signal it.
     */
    private void signalPriceBatchWorkflow(String orderId, String correlationId, int price) {
        SignalPriceBatchWorkflowActivity signalActivity = 
                Workflow.newActivityStub(SignalPriceBatchWorkflowActivity.class, SIGNAL_ACTIVITY_OPTIONS);
        
        signalActivity.signalPriceBatchWorkflow(orderId, correlationId, price);
        
        LOG.info("Triggered price batch workflow signal activity: orderId={}, correlationId={}", orderId, correlationId);
    }

    /**
     * Signal the global stock batching workflow using an activity.
     * The activity will use the signalWithStart pattern to signal the workflow.
     * If workflow doesn't exist, start it. If it exists, just signal it.
     */
    private void signalStockBatchWorkflow(String orderId, String correlationId, int stock) {
        SignalStockBatchWorkflowActivity signalActivity = 
                Workflow.newActivityStub(SignalStockBatchWorkflowActivity.class, SIGNAL_ACTIVITY_OPTIONS);
        
        signalActivity.signalStockBatchWorkflow(orderId, correlationId, stock);
        
        LOG.info("Triggered stock batch workflow signal activity: orderId={}, correlationId={}", orderId, correlationId);
    }

    /**
     * Determine the final order status based on price and stock callback results.
     * This mirrors the logic in SendFinalCallbackActivityImpl.
     * Logic:
     * - COMPLETED: Both isPriceOk and isStockOk are true
     * - PARTIAL: Either isPriceOk or isStockOk is false/null, but not both
     * - FAILED: Both isPriceOk and isStockOk are false/null
     */
    private OrderStatus determineFinalStatus(CallbackResponse priceCallback, CallbackResponse stockCallback) {
        Boolean isPriceOk = priceCallback != null ? priceCallback.isPriceOk() : null;
        Boolean isStockOk = stockCallback != null ? stockCallback.isStockOk() : null;

        // Both callbacks missing or both failed
        if ((isPriceOk == null && isStockOk == null) ||
            (Boolean.FALSE.equals(isPriceOk) && Boolean.FALSE.equals(isStockOk))) {
            return OrderStatus.FAILED;
        }

        // One or both callbacks failed/missing (partial success)
        if (isPriceOk == null || isStockOk == null ||
            Boolean.FALSE.equals(isPriceOk) || Boolean.FALSE.equals(isStockOk)) {
            return OrderStatus.PARTIAL;
        }

        // Both callbacks successful
        return OrderStatus.COMPLETED;
    }
}
