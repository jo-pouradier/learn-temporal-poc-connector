package com.example.temporal.temporal.workflow;

import com.example.shared.model.CallbackResponse;
import com.example.shared.model.PriceAndStockRequest;
import com.example.shared.model.RequestState;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ProcessPriceAndStockWorkflow {
    @WorkflowMethod
    void processPriceAndStock(PriceAndStockRequest request, String correlationId);

    @QueryMethod
    RequestState getState();

    @SignalMethod
    void setPriceResponse(CallbackResponse callbackResponse);
    @SignalMethod
    void setStockResponse(CallbackResponse callbackResponse);
}
