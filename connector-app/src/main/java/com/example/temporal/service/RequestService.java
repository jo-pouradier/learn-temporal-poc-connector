package com.example.temporal.service;

import com.example.shared.exception.NotFoundException;
import com.example.shared.exception.ServiceUnavailableException;
import com.example.shared.exception.ValidationException;
import com.example.shared.model.CallbackReceivedResponse;
import com.example.shared.model.CallbackResponse;
import com.example.shared.model.EventType;
import com.example.shared.model.OrderStatus;
import com.example.shared.model.PagedResponse;
import com.example.shared.model.PriceAndStockRequest;
import com.example.shared.model.PriceAndStockResponse;
import com.example.shared.model.PriceRequest;
import com.example.shared.model.RequestState;
import com.example.shared.model.StateEvent;
import com.example.shared.model.StockRequest;
import com.example.shared.observability.QueueMetrics;
import com.example.shared.observability.RequestMetrics;
import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.repository.ConnectorStateRepository;
import com.example.temporal.repository.ConnectorEventRepository;
import com.example.temporal.repository.PriceQueueRepository;
import com.example.temporal.repository.PriceQueueRepository.PriceJob;
import com.example.temporal.repository.StockQueueRepository;
import com.example.temporal.repository.StockQueueRepository.StockJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RequestService {

    private static final Logger LOG = LoggerFactory.getLogger(RequestService.class);
    
    // Queue names for metrics
    private static final String PRICE_QUEUE = "connector_price";
    private static final String STOCK_QUEUE = "connector_stock";

    private final ConnectorProperties properties;
    private final RestClient restClient;
    private final ConnectorStateRepository stateRepository;
    private final ConnectorEventRepository eventRepository;
    private final PriceQueueRepository priceQueueRepository;
    private final StockQueueRepository stockQueueRepository;
    private final QueueMetrics queueMetrics;
    private final RequestMetrics requestMetrics;

    public RequestService(ConnectorProperties properties, RestClient.Builder restClientBuilder,
                          ConnectorStateRepository stateRepository, ConnectorEventRepository eventRepository,
                          PriceQueueRepository priceQueueRepository, StockQueueRepository stockQueueRepository,
                          QueueMetrics queueMetrics, RequestMetrics requestMetrics) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.stateRepository = stateRepository;
        this.eventRepository = eventRepository;
        this.priceQueueRepository = priceQueueRepository;
        this.stockQueueRepository = stockQueueRepository;
        this.queueMetrics = queueMetrics;
        this.requestMetrics = requestMetrics;
    }

    public ResponseEntity<PriceAndStockResponse> processPriceAndStock(PriceAndStockRequest payload, String providedCorrelationId) {
        String orderId = payload.orderId();
        if (orderId == null || orderId.isBlank()) {
            throw new ValidationException("orderId is required");
        }

        // Check if this order already exists in DB
        RequestState state = stateRepository.findByOrderId(orderId).orElse(null);
        String correlationId;
        
        if (state == null) {
            // New order - create new state
            correlationId = (providedCorrelationId != null && !providedCorrelationId.isBlank())
                    ? providedCorrelationId : UUID.randomUUID().toString();
            state = new RequestState(orderId, correlationId, payload);
            LOG.info("NEW request queuing: orderId={}, correlationId={}, price={}, stock={}",
                    orderId, correlationId, payload.price(), payload.stock());
        } else {
            // Existing order - update and preserve history
            correlationId = state.getCorrelationId();
            state.setOriginalRequest(payload);
            // Reset completion flags for new processing
            state.setPriceCompleted(false);
            state.setStockCompleted(false);
            state.setPriceCallback(null);
            state.setStockCallback(null);
            LOG.info("UPDATE request queuing: orderId={}, correlationId={}, NEW price={}, NEW stock={}",
                    orderId, correlationId, payload.price(), payload.stock());
        }

        requestMetrics.recordRequestReceived();

        state.setStatus(OrderStatus.QUEUED);
        
        // Save state to DB
        stateRepository.save(state);
        
        // Save event
        StateEvent event = new StateEvent(EventType.STATUS_CHANGE, OrderStatus.QUEUED, 
                "Jobs queued: price=" + payload.price() + ", stock=" + payload.stock());
        eventRepository.save(orderId, event);

        // Try to enqueue jobs - check queue size limits
        int currentQueueSize = priceQueueRepository.size() + stockQueueRepository.size();
        if (currentQueueSize >= properties.getQueueSize() * 2) { // Both queues combined
            state.setStatus(OrderStatus.FAILED);
            state.setError("Queue full");
            stateRepository.save(state);
            
            StateEvent errorEvent = new StateEvent(EventType.QUEUE_FULL, OrderStatus.FAILED, "Price or stock queue full");
            eventRepository.save(orderId, errorEvent);
            
            requestMetrics.recordRequestFailed();
            throw new ServiceUnavailableException("Queue full");
        }

        priceQueueRepository.enqueue(orderId, correlationId, payload.price());
        stockQueueRepository.enqueue(orderId, correlationId, payload.stock());
        
        // Record enqueue metrics
        queueMetrics.recordEnqueue(PRICE_QUEUE);
        queueMetrics.recordEnqueue(STOCK_QUEUE);
        
        LOG.info("Jobs queued: orderId={}, correlationId={}, price={}, stock={}",
                orderId, correlationId, payload.price(), payload.stock());

        PriceAndStockResponse response = new PriceAndStockResponse(orderId, "queued");
        return ResponseEntity.accepted()
                .header("X-Correlation-Id", correlationId)
                .body(response);
    }

    public Map<String, Object> processBatchPriceAndStock(List<PriceAndStockRequest> payload) {
        int successCount = 0;
        int failedCount = 0;
        List<String> failedIds = new ArrayList<>();

        for (PriceAndStockRequest request : payload) {
            try {
                // Reuse existing single-item logic
                processPriceAndStock(request, null);
                successCount++;
            } catch (Exception e) {
                LOG.error("Failed to process batch item: orderId={}, error={}", request.orderId(), e.getMessage());
                failedCount++;
                if (request.orderId() != null) {
                    failedIds.add(request.orderId());
                }
            }
        }

        return Map.of(
            "total", payload.size(),
            "queued", successCount,
            "failed", failedCount,
            "failedIds", failedIds
        );
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

    public ResponseEntity<CallbackReceivedResponse> processCallback(String orderId, String type, CallbackResponse callback) {
        LOG.info("Callback received: orderId={}, type={}, correlationId={}", orderId, type, callback.correlationId());
        requestMetrics.recordCallbackReceived();

        RequestState state = stateRepository.findByOrderId(orderId)
            .orElseThrow(() -> new NotFoundException("Request not found: " + orderId));

        switch (type) {
            case "price" -> {
                state.setPriceCallback(callback);
                state.setPriceCompleted(true);
                stateRepository.save(state);
                
                StateEvent event = new StateEvent(EventType.CALLBACK_RECEIVED, OrderStatus.PROCESSING_PRICE, 
                        "Price callback: isPriceOk=" + callback.isPriceOk());
                eventRepository.save(orderId, event);
                
                LOG.info("Price callback processed: orderId={}, priceCompleted={}, stockCompleted={}", 
                        orderId, state.isPriceCompleted(), state.isStockCompleted());
            }
            case "stock" -> {
                state.setStockCallback(callback);
                state.setStockCompleted(true);
                stateRepository.save(state);
                
                StateEvent event = new StateEvent(EventType.CALLBACK_RECEIVED, OrderStatus.PROCESSING_STOCK, 
                        "Stock callback: isStockOk=" + callback.isStockOk());
                eventRepository.save(orderId, event);
                
                LOG.info("Stock callback processed: orderId={}, priceCompleted={}, stockCompleted={}", 
                        orderId, state.isPriceCompleted(), state.isStockCompleted());
            }
            default -> throw new ValidationException("Unknown callback type: " + type);
        }

        if (state.isPriceCompleted() && state.isStockCompleted()) {
            LOG.info("Both callbacks received, combining: orderId={}, correlationId={}", orderId, state.getCorrelationId());
            combineAndSendFinalCallback(state);
        } else {
            LOG.info("Waiting for other callback: orderId={}, priceCompleted={}, stockCompleted={}", 
                    orderId, state.isPriceCompleted(), state.isStockCompleted());
        }

        return ResponseEntity.ok()
                .header("X-Correlation-Id", state.getCorrelationId())
                .body(new CallbackReceivedResponse("callback_received"));
    }

    /**
     * Process price queue batch.
     * Called by QueueProcessorScheduler at configurable interval.
     */
    public void processPriceQueue() {
        int queueSize = priceQueueRepository.size();
        LOG.debug("processPriceQueue invoked: priceQueue.size={}", queueSize);
        if (queueSize == 0) {
            return;
        }

        List<PriceJob> batch = priceQueueRepository.dequeue(10);
        
        LOG.info("Processing Price Batch of size {}", batch.size());
        
        // Record dequeue metrics
        queueMetrics.recordDequeue(PRICE_QUEUE, batch.size());

        for (PriceJob job : batch) {
            LOG.info("Sending price to channel: orderId={}, correlationId={}, price={}", 
                    job.orderId(), job.correlationId(), job.price());
            processJob(job.orderId(), job.correlationId(), "price", properties.getChannelPriceUrl(), new PriceRequest(job.orderId(), job.price()));
        }
        LOG.info("Price batch processed: processed={}", batch.size());
    }

    /**
     * Process stock queue batch.
     * Called by QueueProcessorScheduler at configurable interval.
     */
    public void processStockQueue() {
        int queueSize = stockQueueRepository.size();
        LOG.debug("processStockQueue invoked: stockQueue.size={}", queueSize);
        if (queueSize == 0){
            return;
        }

        List<StockJob> batch = stockQueueRepository.dequeue(10);
        
        LOG.info("Processing Stock Batch of size {}", batch.size());
        
        // Record dequeue metrics
        queueMetrics.recordDequeue(STOCK_QUEUE, batch.size());

        for (StockJob job : batch) {
            LOG.info("Sending stock to channel: orderId={}, correlationId={}, stock={}", 
                    job.orderId(), job.correlationId(), job.stock());
            processJob(job.orderId(), job.correlationId(), "stock", properties.getChannelStockUrl(), new StockRequest(job.orderId(), job.stock()));
        }
        LOG.info("Stock batch processed: processed={}", batch.size());
    }

    /**
     * Scheduled task to update queue and request state gauge metrics.
     * Runs every 5 seconds to keep gauges up-to-date.
     */
    @Scheduled(fixedRate = 5000)
    public void updateMetricsGauges() {
        try {
            // Update queue metrics
            queueMetrics.updateQueueMetrics(PRICE_QUEUE, 
                    priceQueueRepository::size, 
                    priceQueueRepository::getOldestQueuedAt);
            queueMetrics.updateQueueMetrics(STOCK_QUEUE, 
                    stockQueueRepository::size, 
                    stockQueueRepository::getOldestQueuedAt);
            
            // Update request counts by status
            requestMetrics.updateRequestCountsByStatus(stateRepository.countByStatus());
            
            LOG.debug("Metrics gauges updated");
        } catch (Exception e) {
            LOG.warn("Failed to update metrics gauges: {}", e.getMessage());
        }
    }

    private void processJob(String orderId, String correlationId, String type, String url, Object body) {
        RequestState state = stateRepository.findByOrderId(orderId).orElse(null);
        String callbackUrl = properties.getSelfCallbackUrl() + "/" + orderId + "?type=" + type;
        
        LOG.info("Processing job: orderId={}, correlationId={}, type={}, url={}, callbackUrl={}", 
                orderId, correlationId, type, url, callbackUrl);
        
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-Id", correlationId)
                    .header("X-Callback-Url", callbackUrl)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            if (state != null) {
                OrderStatus newStatus = type.equals("price") 
                    ? OrderStatus.PROCESSING_PRICE 
                    : OrderStatus.PROCESSING_STOCK;
                state.setStatus(newStatus);
                stateRepository.save(state);
                
                StateEvent event = new StateEvent(EventType.JOB_SENT_TO_CHANNEL, newStatus, 
                        "Job sent to channel: " + type);
                eventRepository.save(orderId, event);
            }
            LOG.info("Job sent to channel successfully: orderId={}, correlationId={}, type={}", orderId, correlationId, type);

        } catch (Exception e) {
            LOG.error("Failed {} job: orderId={}, correlationId={}, error={}", type, orderId, correlationId, e.getMessage());
            if (state != null) {
                state.setStatus(OrderStatus.FAILED);
                state.setError(e.getMessage());
                stateRepository.save(state);
                
                StateEvent event = new StateEvent(EventType.ERROR, OrderStatus.FAILED, 
                        "Failed " + type + " job: " + e.getMessage());
                eventRepository.save(orderId, event);
            }
            sendErrorCallback(state, type, e.getMessage());
        }
    }

    private void combineAndSendFinalCallback(RequestState state) {
        LOG.info("Combining callbacks: orderId={}, correlationId={}, priceCallback={}, stockCallback={}", 
                state.getOrderId(), state.getCorrelationId(), 
                state.getPriceCallback() != null, state.getStockCallback() != null);
        
        CallbackResponse.Builder builder = CallbackResponse.builder()
                .correlationId(state.getCorrelationId())
                .status("completed")
                .type("combined")
                .processedAt(LocalDateTime.now());

        if (state.getPriceCallback() != null) {
            builder.price(state.getPriceCallback().price());
            builder.priceOk(state.getPriceCallback().isPriceOk());
        }
        if (state.getStockCallback() != null) {
            builder.stock(state.getStockCallback().stock());
            builder.stockOk(state.getStockCallback().isStockOk());
        }

        CallbackResponse combined = builder.build();
        state.setFinalResponse(combined);
        state.setCompletedAt(LocalDateTime.now());
        state.setStatus(OrderStatus.COMPLETED);
        
        stateRepository.save(state);
        
        StateEvent event = new StateEvent(EventType.STATUS_CHANGE, OrderStatus.COMPLETED, 
                "Both callbacks received and combined");
        eventRepository.save(state.getOrderId(), event);
        
        requestMetrics.recordRequestCompleted();

        // Record end-to-end latency
        if (state.getSubmittedAt() != null && state.getCompletedAt() != null) {
            Duration latency = Duration.between(state.getSubmittedAt(), state.getCompletedAt());
            requestMetrics.recordCallbackLatency(latency);
        }

        LOG.info("Sending final callback: orderId={}, correlationId={}", state.getOrderId(), state.getCorrelationId());
        sendCallback(properties.getCallbackUrlBase() + "/" + state.getOrderId(), combined);
    }

    private void sendErrorCallback(RequestState state, String type, String errorMessage) {
        if (state == null) return;
        requestMetrics.recordRequestFailed();

        CallbackResponse errorResponse = CallbackResponse.builder()
                .correlationId(state.getCorrelationId())
                .status("error")
                .type(type)
                .error(errorMessage)
                .processedAt(LocalDateTime.now())
                .build();
        sendCallback(properties.getCallbackUrlBase() + "/" + state.getOrderId(), errorResponse);
    }

    private void sendCallback(String url, CallbackResponse response) {
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response)
                    .retrieve()
                    .toBodilessEntity();
            requestMetrics.recordCallbackSent();
            LOG.info("Callback sent: correlationId={}, status={}", response.correlationId(), response.status());
        } catch (Exception e) {
            requestMetrics.recordRequestFailed();
            LOG.error("Callback failed: correlationId={}, error={}", response.correlationId(), e.getMessage());
        }
    }

    public Map<String, Object> clearAllQueues() {
        LOG.info("Clearing all connector queues and state");
        
        int priceQueueCleared = priceQueueRepository.size();
        int stockQueueCleared = stockQueueRepository.size();
        int statesCleared = stateRepository.count();
        
        priceQueueRepository.clear();
        stockQueueRepository.clear();
        eventRepository.deleteAll();
        // Delete all states (cascade will delete events and queue entries)
        stateRepository.findAll().forEach(state -> stateRepository.delete(state.getOrderId()));
        
        String timestamp = LocalDateTime.now().toString();
        
        LOG.info("Cleared {} price jobs, {} stock jobs, {} request states at {}", 
                priceQueueCleared, stockQueueCleared, statesCleared, timestamp);
        
        return Map.of(
                "priceQueueCleared", priceQueueCleared,
                "stockQueueCleared", stockQueueCleared,
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
