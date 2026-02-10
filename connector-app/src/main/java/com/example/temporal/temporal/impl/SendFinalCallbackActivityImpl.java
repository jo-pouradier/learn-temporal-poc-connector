package com.example.temporal.temporal.impl;

import com.example.shared.exception.NotFoundException;
import com.example.shared.model.CallbackResponse;
import com.example.shared.model.EventType;
import com.example.shared.model.OrderStatus;
import com.example.shared.model.RequestState;
import com.example.shared.model.StateEvent;
import com.example.shared.observability.RequestMetrics;
import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.repository.ConnectorEventRepository;
import com.example.temporal.repository.ConnectorStateRepository;
import com.example.temporal.temporal.activities.SendFinalCallbackActivity;
import io.temporal.spring.boot.ActivityImpl;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;

@ActivityImpl
@Service
public class SendFinalCallbackActivityImpl implements SendFinalCallbackActivity {
    private static final Logger LOG = Workflow.getLogger(SendFinalCallbackActivityImpl.class);

    private final ConnectorStateRepository stateRepository;
    private final ConnectorEventRepository eventRepository;
    private final ConnectorProperties properties;
    private final RestClient restClient;
    private final RequestMetrics requestMetrics;

    public SendFinalCallbackActivityImpl(ConnectorStateRepository stateRepository,
                                         ConnectorEventRepository eventRepository,
                                         ConnectorProperties properties,
                                         RestClient.Builder restClientBuilder,
                                         RequestMetrics requestMetrics) {
        this.stateRepository = stateRepository;
        this.eventRepository = eventRepository;
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.requestMetrics = requestMetrics;
    }

    @Override
    public void sendFinalCallback(CallbackResponse priceCallback,
                                  CallbackResponse stockCallback,
                                  String orderId,
                                  String correlationId) {
        LOG.info("Combining callbacks and sending final response: orderId={}, correlationId={}", 
                orderId, correlationId);

        // 1. Combine callbacks into final response
        CallbackResponse.Builder builder = CallbackResponse.builder()
                .correlationId(correlationId)
                .status("completed")
                .type("combined")
                .processedAt(LocalDateTime.now());

        if (priceCallback != null) {
            builder.price(priceCallback.price());
            builder.priceOk(priceCallback.isPriceOk());
        }
        
        if (stockCallback != null) {
            builder.stock(stockCallback.stock());
            builder.stockOk(stockCallback.isStockOk());
        }

        CallbackResponse combined = builder.build();

        // 2. Determine final status based on callback results
        OrderStatus finalStatus = determineOrderStatus(priceCallback, stockCallback);
        
        // 3. Update database state
        RequestState state = stateRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("State not found: " + orderId));
        
        state.setFinalResponse(combined);
        state.setCompletedAt(LocalDateTime.now());
        state.setStatus(finalStatus);
        stateRepository.save(state);

        // 3. Record event
        StateEvent event = new StateEvent(EventType.STATUS_CHANGE, finalStatus,
                "Both callbacks received and combined");
        eventRepository.save(orderId, event);

        // 4. Send HTTP callback to main-app
        String callbackUrl = properties.getCallbackUrlBase() + "/" + orderId;
        
        try {
            restClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(combined)
                    .retrieve()
                    .toBodilessEntity();

            LOG.info("Final callback sent successfully: orderId={}, correlationId={}, url={}", 
                    orderId, correlationId, callbackUrl);
            
            // 5. Record metrics
            requestMetrics.recordCallbackSent();
            requestMetrics.recordRequestCompleted();
            
            // Record end-to-end latency
            if (state.getSubmittedAt() != null && state.getCompletedAt() != null) {
                Duration latency = Duration.between(state.getSubmittedAt(), state.getCompletedAt());
                requestMetrics.recordCallbackLatency(latency);
            }
        } catch (Exception e) {
            LOG.error("Failed to send final callback: orderId={}, correlationId={}, error={}", 
                    orderId, correlationId, e.getMessage());
            
            // Update state to failed
            state.setStatus(OrderStatus.FAILED);
            state.setError("Failed to send callback: " + e.getMessage());
            stateRepository.save(state);
            
            StateEvent errorEvent = new StateEvent(EventType.ERROR, OrderStatus.FAILED,
                    "Failed to send final callback: " + e.getMessage());
            eventRepository.save(orderId, errorEvent);
            
            requestMetrics.recordRequestFailed();
            
            // Re-throw to trigger Temporal retry
            throw new RuntimeException("Failed to send final callback", e);
        }
    }

    /**
     * Determine the final order status based on price and stock callback results.
     * 
     * Logic:
     * - COMPLETED: Both isPriceOk and isStockOk are true
     * - PARTIAL: Either isPriceOk or isStockOk is false/null, but not both
     * - FAILED: Both isPriceOk and isStockOk are false/null
     */
    private OrderStatus determineOrderStatus(CallbackResponse priceCallback, CallbackResponse stockCallback) {
        Boolean isPriceOk = priceCallback != null ? priceCallback.isPriceOk() : null;
        Boolean isStockOk = stockCallback != null ? stockCallback.isStockOk() : null;
        
        // Both callbacks missing or both failed
        if ((isPriceOk == null && isStockOk == null) || 
            (Boolean.FALSE.equals(isPriceOk) && Boolean.FALSE.equals(isStockOk))) {
            LOG.warn("Order failed: isPriceOk={}, isStockOk={}", isPriceOk, isStockOk);
            return OrderStatus.FAILED;
        }
        
        // One or both callbacks failed/missing (partial success)
        if (isPriceOk == null || isStockOk == null || 
            Boolean.FALSE.equals(isPriceOk) || Boolean.FALSE.equals(isStockOk)) {
            LOG.warn("Order partially completed: isPriceOk={}, isStockOk={}", isPriceOk, isStockOk);
            return OrderStatus.PARTIAL;
        }
        
        // Both callbacks successful
        LOG.info("Order completed successfully: isPriceOk={}, isStockOk={}", isPriceOk, isStockOk);
        return OrderStatus.COMPLETED;
    }
}
