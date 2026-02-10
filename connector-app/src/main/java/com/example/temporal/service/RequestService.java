package com.example.temporal.service;

import com.example.shared.exception.NotFoundException;
import com.example.shared.exception.ValidationException;
import com.example.shared.model.CallbackResponse;
import com.example.shared.model.EventType;
import com.example.shared.model.OrderStatus;
import com.example.shared.model.PagedResponse;
import com.example.shared.model.PriceAndStockRequest;
import com.example.shared.model.PriceAndStockResponse;
import com.example.shared.model.RequestState;
import com.example.shared.model.StateEvent;
import com.example.shared.observability.RequestMetrics;
import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.repository.ConnectorStateRepository;
import com.example.temporal.repository.ConnectorEventRepository;
import com.example.temporal.temporal.utils.WorkflowIdBuilder;
import com.example.temporal.temporal.workflow.ProcessPriceAndStockWorkflow;
import com.example.temporal.temporal.worker.ProcessPriceAndStockWorker;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;
import io.temporal.common.SearchAttributeKey;
import io.temporal.common.SearchAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RequestService {

    private static final Logger LOG = LoggerFactory.getLogger(RequestService.class);

    private final ConnectorProperties properties;
    private final RestClient restClient;
    private final ConnectorStateRepository stateRepository;
    private final ConnectorEventRepository eventRepository;
    private final RequestMetrics requestMetrics;
    private final WorkflowClient workflowClient;

    public RequestService(ConnectorProperties properties, RestClient.Builder restClientBuilder,
                          ConnectorStateRepository stateRepository, ConnectorEventRepository eventRepository,
                          RequestMetrics requestMetrics, WorkflowClient workflowClient) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.stateRepository = stateRepository;
        this.eventRepository = eventRepository;
        this.requestMetrics = requestMetrics;
        this.workflowClient = workflowClient;
    }

    public PriceAndStockResponse processPriceAndStock(PriceAndStockRequest payload,@Nullable String providedCorrelationId) {
        providedCorrelationId = (providedCorrelationId == null || providedCorrelationId.isBlank())
                ? UUID.randomUUID().toString() : providedCorrelationId;
        String orderId = payload.orderId();

        // save in DB
        String finalProvidedCorrelationId = providedCorrelationId;
        RequestState state = stateRepository.findByOrderId(orderId)
                .map(existing -> updateExistingState(payload, existing, finalProvidedCorrelationId))
                .orElseGet(() -> getCreateNewState(payload, finalProvidedCorrelationId, orderId));
        requestMetrics.recordRequestReceived();
        state.setStatus(OrderStatus.QUEUED);
        stateRepository.save(state);

        StateEvent event = new StateEvent(EventType.STATUS_CHANGE, OrderStatus.QUEUED,
                "Workflow starting");
        eventRepository.save(orderId, event);

        // Start Temporal workflow
        String workflowId = WorkflowIdBuilder.priceStockWorkflowId(orderId);
        SearchAttributes correlationIdSearchAttribute = SearchAttributes.newBuilder()
                .set(SearchAttributeKey.forKeyword("correlationId"), providedCorrelationId)
                .build();
        ProcessPriceAndStockWorkflow workflow = workflowClient.newWorkflowStub(
                ProcessPriceAndStockWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_TERMINATE_EXISTING) // last event win
                        .setTaskQueue(ProcessPriceAndStockWorker.QUEUE)
                        .setRetryOptions(RetryOptions.newBuilder()
                                .setInitialInterval(Duration.ofSeconds(1))
                                .setMaximumAttempts(3)
                                .setBackoffCoefficient(2)
                                .build())
                        .setTypedSearchAttributes(
                                correlationIdSearchAttribute)
                        .build()
        );

        // Start workflow asynchronously
        WorkflowClient.start(workflow::processPriceAndStock, payload, providedCorrelationId);

        LOG.info("Workflow started: orderId={}, workflowId={}, correlationId={}",
                orderId, workflowId, providedCorrelationId);

        return  new PriceAndStockResponse(orderId, "queued");
    }

    private static RequestState getCreateNewState(PriceAndStockRequest payload, String providedCorrelationId, String orderId) {
        LOG.info("NEW request: orderId={}, correlationId={}, price={}, stock={}",
                orderId, providedCorrelationId, payload.price(), payload.stock());
        return new RequestState(orderId, providedCorrelationId, payload);
    }

    private static RequestState updateExistingState(PriceAndStockRequest payload, RequestState state, String orderId) {
        var correlationId = state.getCorrelationId();
        state.setOriginalRequest(payload);
        state.resetFlags();
        LOG.info("UPDATE request: orderId={}, correlationId={}, NEW price={}, NEW stock={}",
                orderId, correlationId, payload.price(), payload.stock());
        return state;
    }

    public RequestState getStatus(String orderId) {
        return stateRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Request not found: " + orderId));
    }

    @Transactional
    public PagedResponse<RequestState> getAllStatus(String filter, int page, int size) {
        int offset = page * size;
        List<RequestState> states = stateRepository.findAllPaginated(offset, size, filter);
        long totalElements = stateRepository.countFiltered(filter);
        return PagedResponse.of(states, page, size, totalElements);
    }

    public void processCallback(String orderId, String type, CallbackResponse callback) {
        requestMetrics.recordCallbackReceived();
        RequestState state = stateRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Request not found: " + orderId));

        StateEvent event = switch (type) {
            case "price" -> state.updatePrice(callback);
            case "stock" -> state.updateStock(callback);
            default -> throw new ValidationException("Unknown callback type: " + type);
        };
        stateRepository.save(state);
        eventRepository.save(orderId, event);
        LOG.info("{} callback processed: orderId={}, priceDone={}, stockDone={}",
                type, orderId, state.isPriceCompleted(), state.isStockCompleted());

        // Signal the Temporal workflow
        String workflowId = WorkflowIdBuilder.priceStockWorkflowId(orderId);
        ProcessPriceAndStockWorkflow workflow = workflowClient.newWorkflowStub(
                ProcessPriceAndStockWorkflow.class, workflowId);

        if (type.equals("price")) {
            workflow.setPriceResponse(callback);
        } else {
            workflow.setStockResponse(callback);
        }

        LOG.info("Workflow signaled: workflowId={}, type={}", workflowId, type);

    }

    /**
     * Scheduled task to update request state gauge metrics.
     * Runs every 5 seconds to keep gauges up-to-date.
     */
    @Scheduled(fixedRate = 5000)
    public void updateMetricsGauges() {
        try {
            // Update request counts by status
            requestMetrics.updateRequestCountsByStatus(stateRepository.countByStatus());

            LOG.debug("Metrics gauges updated");
        } catch (Exception e) {
            LOG.warn("Failed to update metrics gauges: {}", e.getMessage());
        }
    }

    public void processJobRequest(String orderId, String correlationId, String type, String url, Object body) {
        String callbackUrl = properties.getSelfCallbackUrl() + "/" + orderId + "?type=" + type;
        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", correlationId)
                .header("X-Callback-Url", callbackUrl)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public Map<String, Object> clearAllQueues() {
        LOG.info("Clearing all connector state and events");

        int statesCleared = stateRepository.count();

        // Delete all events first
        eventRepository.deleteAll();

        // Delete all states (cascade will delete related records)
        stateRepository.findAll().forEach(state -> stateRepository.delete(state.getOrderId()));

        String timestamp = LocalDateTime.now().toString();

        LOG.info("Cleared {} request states at {}", statesCleared, timestamp);

        return Map.of(
                "statesCleared", statesCleared,
                "timestamp", timestamp
        );
    }

    public List<StateEvent> getHistory(String orderId) {
        // Verify order exists
        if (!stateRepository.findByOrderId(orderId).isPresent()) {
            throw new NotFoundException("Request not found: " + orderId);
        }
        return eventRepository.findByOrderId(orderId);
    }
}
